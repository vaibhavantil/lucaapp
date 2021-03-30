package de.culture4life.luca.checkin;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;

import de.culture4life.luca.BuildConfig;
import de.culture4life.luca.LucaApplication;
import de.culture4life.luca.Manager;
import de.culture4life.luca.R;
import de.culture4life.luca.crypto.AsymmetricCipherProvider;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.crypto.TraceIdWrapper;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.location.GeofenceException;
import de.culture4life.luca.location.GeofenceManager;
import de.culture4life.luca.location.LocationManager;
import de.culture4life.luca.meeting.MeetingAdditionalData;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.pojo.AdditionalCheckInPropertiesRequestData;
import de.culture4life.luca.network.pojo.CheckInRequestData;
import de.culture4life.luca.network.pojo.CheckOutRequestData;
import de.culture4life.luca.network.pojo.TraceData;
import de.culture4life.luca.notification.LucaNotificationManager;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.ui.MainActivity;
import de.culture4life.luca.ui.qrcode.QrCodeData;
import de.culture4life.luca.util.SerializationUtil;
import de.culture4life.luca.util.TimeUtil;

import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static de.culture4life.luca.location.GeofenceManager.MAXIMUM_GEOFENCE_RADIUS;
import static de.culture4life.luca.location.GeofenceManager.MINIMUM_GEOFENCE_RADIUS;
import static de.culture4life.luca.location.GeofenceManager.UPDATE_INTERVAL_DEFAULT;
import static de.culture4life.luca.notification.LucaNotificationManager.NOTIFICATION_ID_EVENT;
import static de.culture4life.luca.util.SerializationUtil.serializeToBase64;

public class CheckInManager extends Manager {

    public static final String KEY_CHECKED_IN_TRACE_ID = "checked_in_trace_id";
    public static final String KEY_CHECKED_IN_VENUE_ID = "checked_in_venue_id";
    public static final String KEY_CHECK_IN_TIMESTAMP = "check_in_timestamp";
    public static final String KEY_CHECK_IN_DATA = "check_in_data_2";
    public static final String KEY_ARCHIVED_CHECK_IN_DATA = "archived_check_in_data";

    private static final long MINIMUM_CHECK_IN_DURATION = TimeUnit.MINUTES.toMillis(1);
    private static final long LOCATION_REQUEST_TIMEOUT = TimeUnit.SECONDS.toMillis(3);
    private static final int RECENT_TRACE_IDS_LIMIT = (int) TimeUnit.HOURS.toMinutes(6);
    private static final long CHECK_OUT_POLLING_INTERVAL = TimeUnit.MINUTES.toMillis(1);
    private static final long AUTOMATIC_CHECK_OUT_RETRY_DELAY = BuildConfig.DEBUG ? TimeUnit.SECONDS.toMillis(15) : TimeUnit.MINUTES.toMillis(2);

    private final PreferencesManager preferencesManager;
    private final NetworkManager networkManager;
    private final GeofenceManager geofenceManager;
    private final LocationManager locationManager;
    private final CryptoManager cryptoManager;
    private final HistoryManager historyManager;
    private final LucaNotificationManager notificationManager;

    private boolean skipMinimumCheckInDurationAssertion;
    private boolean skipMinimumDistanceAssertion;

    @Nullable
    private MeetingAdditionalData meetingAdditionalData;

    @Nullable
    private CheckInData checkInData;

    @Nullable
    private GeofencingRequest autoCheckoutGeofenceRequest;

    public CheckInManager(@NonNull PreferencesManager preferencesManager, @NonNull NetworkManager networkManager, @NonNull GeofenceManager geofenceManager, @NonNull LocationManager locationManager, @NonNull HistoryManager historyManager, @NonNull CryptoManager cryptoManager, @NonNull LucaNotificationManager notificationManager) {
        this.preferencesManager = preferencesManager;
        this.networkManager = networkManager;
        this.geofenceManager = geofenceManager;
        this.locationManager = locationManager;
        this.historyManager = historyManager;
        this.cryptoManager = cryptoManager;
        this.notificationManager = notificationManager;

        skipMinimumDistanceAssertion = true;
        if (BuildConfig.DEBUG) {
            skipMinimumCheckInDurationAssertion = true;
        }
    }

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return Completable.mergeArray(
                preferencesManager.initialize(context),
                networkManager.initialize(context),
                geofenceManager.initialize(context),
                locationManager.initialize(context),
                historyManager.initialize(context),
                cryptoManager.initialize(context),
                notificationManager.initialize(context)
        ).andThen(Completable.mergeArray(
                deleteOldArchivedCheckInData(),
                Completable.fromAction(() ->
                        managerDisposable.add(getCheckInDataChanges()
                                .doOnNext(updatedCheckInData -> {
                                    Timber.d("Check-in data updated: %s", updatedCheckInData);
                                    this.checkInData = updatedCheckInData;
                                })
                                .subscribe()))
        ));
    }

    /*
        Check-in
     */

    public Completable checkIn(@NonNull UUID scannerId, @NonNull QrCodeData qrCodeData) {
        return assertNotCheckedIn()
                .andThen(generateCheckInData(qrCodeData, scannerId)
                        .flatMapCompletable(checkInRequestData -> networkManager.getLucaEndpoints().checkIn(checkInRequestData)))
                .andThen(getCheckInDataFromBackend()
                        .switchIfEmpty(Single.error(new IllegalStateException("No check-in data available at backend after checking in"))))
                .flatMapCompletable(this::processCheckIn);
    }

    /**
     * Should be called after a check-in occurred (either triggered by the user or in the backend)
     */
    private Completable processCheckIn(@NonNull CheckInData checkInData) {
        return Completable.fromAction(() -> this.checkInData = checkInData)
                .andThen(preferencesManager.containsKey(KEY_CHECK_IN_DATA))
                .flatMap(oldCheckInDataAvailable -> Single.defer(() -> {
                    if (oldCheckInDataAvailable) {
                        return preferencesManager.restore(KEY_CHECK_IN_DATA, CheckInData.class)
                                .map(oldCheckInData -> !oldCheckInData.getTraceId().equals(checkInData.getTraceId()));
                    } else {
                        return Single.just(true);
                    }
                }))
                .flatMapCompletable(isNewCheckIn -> Completable.defer(() -> {
                    if (isNewCheckIn) {
                        return addCheckInDataToArchive(checkInData)
                                .andThen(historyManager.addCheckInItem(checkInData));
                    } else {
                        return Completable.complete();
                    }
                }))
                .andThen(persistCheckInData(checkInData));
    }

    public Single<ECPublicKey> getLocationPublicKey(@NonNull UUID scannerId) {
        return networkManager.getLucaEndpoints().getScanner(scannerId.toString())
                .map(jsonObject -> jsonObject.get("publicKey").getAsString())
                .flatMap(SerializationUtil::deserializeFromBase64)
                .flatMap(AsymmetricCipherProvider::decodePublicKey);
    }

    private Single<CheckInRequestData> generateCheckInData(@NonNull QrCodeData qrCodeData, @NonNull UUID scannerId) {
        return getLocationPublicKey(scannerId)
                .flatMap(locationPublicKey -> generateCheckInData(qrCodeData, locationPublicKey))
                .doOnSuccess(checkInRequestData -> checkInRequestData.setScannerId(scannerId.toString()));
    }

    private Single<CheckInRequestData> generateCheckInData(@NonNull QrCodeData qrCodeData, @NonNull PublicKey locationPublicKey) {
        return Single.fromCallable(() -> {
            CheckInRequestData checkInRequestData = new CheckInRequestData();

            checkInRequestData.setDeviceType(qrCodeData.getDeviceType());

            long timestamp = TimeUtil.decodeUnixTimestamp(qrCodeData.getTimestamp())
                    .flatMap(TimeUtil::roundUnixTimestampDownToMinute).blockingGet();
            checkInRequestData.setUnixTimestamp(timestamp);

            String serialisedTraceId = serializeToBase64(qrCodeData.getTraceId()).blockingGet();
            checkInRequestData.setTraceId(serialisedTraceId);

            KeyPair scannerEphemeralKeyPair = cryptoManager.generateScannerEphemeralKeyPair().blockingGet();
            cryptoManager.persistScannerEphemeralKeyPair(scannerEphemeralKeyPair).blockingAwait();

            String serializedScannerPublicKey = AsymmetricCipherProvider.encode((ECPublicKey) scannerEphemeralKeyPair.getPublic())
                    .flatMap(SerializationUtil::serializeToBase64).blockingGet();
            checkInRequestData.setScannerEphemeralPublicKey(serializedScannerPublicKey);

            byte[] iv = cryptoManager.generateSecureRandomData(16).blockingGet();
            String encodedIv = serializeToBase64(iv).blockingGet();
            checkInRequestData.setIv(encodedIv);

            byte[] diffieHellmanSecret = cryptoManager.getAsymmetricCipherProvider()
                    .generateSecret(scannerEphemeralKeyPair.getPrivate(), locationPublicKey).blockingGet();

            byte[] encryptedQrCodeData = encryptQrCodeData(qrCodeData, iv, diffieHellmanSecret).blockingGet();
            String serialisedEncryptedQrCodeData = serializeToBase64(encryptedQrCodeData).blockingGet();
            checkInRequestData.setReEncryptedQrCodeData(serialisedEncryptedQrCodeData);

            String serialisedMac = createQrCodeDataMac(encryptedQrCodeData, diffieHellmanSecret)
                    .flatMap(SerializationUtil::serializeToBase64).blockingGet();
            checkInRequestData.setMac(serialisedMac);

            return checkInRequestData;
        });
    }

    private Single<byte[]> encryptQrCodeData(@NonNull QrCodeData qrCodeData, @NonNull byte[] iv, @NonNull byte[] diffieHellmanSecret) {
        return cryptoManager.generateDataEncryptionSecret(diffieHellmanSecret)
                .flatMap(CryptoManager::createKeyFromSecret)
                .flatMap(encryptionKey -> Single.fromCallable(
                        () -> ByteBuffer.allocate(75)
                                .put((byte) 3)
                                .put(qrCodeData.getKeyId())
                                .put(qrCodeData.getUserEphemeralPublicKey())
                                .put(qrCodeData.getVerificationTag())
                                .put(qrCodeData.getEncryptedData())
                                .array())
                        .flatMap(encodedQrCodeData -> cryptoManager.getSymmetricCipherProvider().encrypt(encodedQrCodeData, iv, encryptionKey)))
                .doOnSuccess(bytes -> Timber.d("Encrypted QR code data: %s to %s", qrCodeData.toString(), SerializationUtil.serializeToBase64(bytes).blockingGet()));
    }

    public Single<byte[]> createQrCodeDataMac(byte[] encryptedQrCodeData, @NonNull byte[] diffieHellmanSecret) {
        return cryptoManager.generateDataAuthenticationSecret(diffieHellmanSecret)
                .flatMap(CryptoManager::createKeyFromSecret)
                .flatMap(dataAuthenticationKey -> cryptoManager.getMacProvider().sign(encryptedQrCodeData, dataAuthenticationKey));
    }

    public Single<Boolean> isCheckedIn() {
        return getCheckInDataIfAvailable()
                .isEmpty()
                .map(notCheckedIn -> !notCheckedIn);
    }

    public Completable assertCheckedIn() {
        return isCheckedIn()
                .flatMapCompletable(isCheckedIn -> {
                    if (isCheckedIn) {
                        return Completable.complete();
                    } else {
                        return Completable.error(new IllegalStateException("Not currently checked in, need to check in first"));
                    }
                });
    }

    public Completable assertNotCheckedIn() {
        return isCheckedIn()
                .flatMapCompletable(isCheckedIn -> {
                    if (isCheckedIn) {
                        return Completable.error(new IllegalStateException("Already checked in, need to checkout first"));
                    } else {
                        return Completable.complete();
                    }
                });
    }

    public Observable<Boolean> getCheckedInStateChanges() {
        return Observable.interval(1, TimeUnit.SECONDS)
                .flatMapSingle(tick -> isCheckedIn())
                .distinctUntilChanged();
    }

    public Single<Boolean> isCheckedInAtBackend() {
        return getTraceDataFromBackend()
                .flatMap(traceData -> TimeUtil.convertFromUnixTimestamp(traceData.getCheckOutTimestamp())
                        .toMaybe()
                        .map(checkoutTimestamp -> checkoutTimestamp == 0 || checkoutTimestamp > System.currentTimeMillis()))
                .defaultIfEmpty(false)
                .doOnSubscribe(disposable -> Timber.d("Requesting check-in status from backend"));
    }

    /*
        Additional check-in properties
     */

    public Completable addAdditionalCheckInProperties(@NonNull JsonObject properties, @NonNull PublicKey locationPublicKey) {
        return assertCheckedIn()
                .andThen(getCheckedInTraceId())
                .toSingle()
                .flatMap(traceId -> generateAdditionalCheckInProperties(properties, traceId, locationPublicKey))
                .flatMapCompletable(requestData -> networkManager.getLucaEndpoints().addAdditionalCheckInProperties(requestData));
    }

    private Single<AdditionalCheckInPropertiesRequestData> generateAdditionalCheckInProperties(@NonNull JsonObject properties, @NonNull byte[] traceId, @NonNull PublicKey locationPublicKey) {
        return Single.fromCallable(() -> {
            AdditionalCheckInPropertiesRequestData additionalCheckInProperties = new AdditionalCheckInPropertiesRequestData();

            String serializedTraceId = serializeToBase64(traceId).blockingGet();
            additionalCheckInProperties.setTraceId(serializedTraceId);

            byte[] iv = cryptoManager.generateSecureRandomData(16).blockingGet();
            String encodedIv = serializeToBase64(iv).blockingGet();
            additionalCheckInProperties.setIv(encodedIv);

            KeyPair scannerEphemeralKeyPair = cryptoManager.getScannerEphemeralKeyPair().blockingGet();
            byte[] encryptedProperties = encryptAdditionalCheckInProperties(properties, iv, scannerEphemeralKeyPair.getPrivate(), locationPublicKey).blockingGet();
            String serializedEncryptedProperties = serializeToBase64(encryptedProperties).blockingGet();
            additionalCheckInProperties.setEncryptedProperties(serializedEncryptedProperties);

            String serializedMac = createAdditionalPropertiesMac(encryptedProperties, scannerEphemeralKeyPair.getPrivate(), locationPublicKey)
                    .flatMap(SerializationUtil::serializeToBase64).blockingGet();
            additionalCheckInProperties.setMac(serializedMac);

            String serializedScannerPublicKey = AsymmetricCipherProvider.encode((ECPublicKey) scannerEphemeralKeyPair.getPublic())
                    .flatMap(SerializationUtil::serializeToBase64).blockingGet();
            additionalCheckInProperties.setScannerPublicKey(serializedScannerPublicKey);

            return additionalCheckInProperties;
        });
    }

    private Single<byte[]> encryptAdditionalCheckInProperties(@NonNull JsonObject properties, @NonNull byte[] iv, @NonNull PrivateKey scannerEphemeralPrivateKey, @NonNull PublicKey locationPublicKey) {
        return cryptoManager.getAsymmetricCipherProvider().generateSecret(scannerEphemeralPrivateKey, locationPublicKey)
                .flatMap(cryptoManager::generateDataEncryptionSecret)
                .flatMap(CryptoManager::createKeyFromSecret)
                .flatMap(encryptionKey -> Single.fromCallable(() -> new Gson().toJson(properties))
                        .map(serializedProperties -> serializedProperties.getBytes(StandardCharsets.UTF_8))
                        .flatMap(encodedQrCodeData -> cryptoManager.getSymmetricCipherProvider().encrypt(encodedQrCodeData, iv, encryptionKey)));
    }

    public Single<byte[]> createAdditionalPropertiesMac(byte[] encryptedProperties, @NonNull PrivateKey scannerEphemeralPrivateKey, @NonNull PublicKey locationPublicKey) {
        return cryptoManager.getAsymmetricCipherProvider().generateSecret(scannerEphemeralPrivateKey, locationPublicKey)
                .flatMap(cryptoManager::generateDataAuthenticationSecret)
                .flatMap(CryptoManager::createKeyFromSecret)
                .flatMap(dataAuthenticationKey -> cryptoManager.getMacProvider().sign(encryptedProperties, dataAuthenticationKey));
    }

    /*
        Check-out
     */

    @SuppressLint("MissingPermission")
    public Completable checkOut() {
        return assertCheckedIn()
                .andThen(assertMinimumCheckInDuration())
                .andThen(assertMinimumDistanceToLocation())
                .andThen(networkManager.assertNetworkConnected())
                .andThen(generateCheckOutData()
                        .doOnSuccess(checkOutRequestData -> Timber.i("Generated checkout data: %s", checkOutRequestData))
                        .flatMapCompletable(checkOutRequestData -> networkManager.getLucaEndpoints()
                                .checkOut(checkOutRequestData)
                                .onErrorResumeNext(throwable -> {
                                    if (NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_NOT_FOUND)) {
                                        // user is currently not checked-in
                                        return Completable.complete();
                                    }
                                    return Completable.error(throwable);
                                }))
                        .onErrorResumeNext(throwable -> removeCheckInDataIfCheckedOut()
                                .andThen(Completable.error(throwable))))
                .andThen(processCheckOut())
                .doOnSubscribe(disposable -> Timber.d("Initiating checkout"))
                .doOnComplete(() -> Timber.i("Successfully checked out"));
    }

    /**
     * Should be called after a check-out occurred (either triggered by the user or in the backend)
     */
    private Completable processCheckOut() {
        return getCheckInDataIfAvailable()
                .flatMapCompletable(historyManager::addCheckOutItem)
                .andThen(removeCheckInData())
                .andThen(disableAutomaticCheckOut());
    }

    private Single<CheckOutRequestData> generateCheckOutData() {
        return Single.just(new CheckOutRequestData())
                .flatMap(checkOutRequestData -> getCheckedInTraceId()
                        .toSingle()
                        .flatMap(traceId -> Completable.mergeArray(
                                SerializationUtil.serializeToBase64(traceId)
                                        .doOnSuccess(checkOutRequestData::setTraceId)
                                        .ignoreElement(),
                                TimeUtil.getCurrentUnixTimestamp()
                                        .flatMap(TimeUtil::roundUnixTimestampDownToMinute)
                                        .doOnSuccess(checkOutRequestData::setRoundedUnixTimestamp)
                                        .ignoreElement()
                        ).toSingle(() -> checkOutRequestData)));
    }

    @RequiresPermission("android.permission.ACCESS_FINE_LOCATION")
    public Completable enableAutomaticCheckOut() {
        return createAutoCheckoutGeofenceRequest()
                .flatMapObservable(geofenceManager::getGeofenceEvents)
                .firstElement()
                .ignoreElement()
                .andThen(performAutomaticCheckout()
                        .doOnError(throwable -> Timber.w("Unable to perform automatic check-out: %s", throwable.toString()))
                        .retryWhen(errors -> errors
                                .doOnNext(throwable -> Timber.v("Retrying automatic check-out in %d seconds", TimeUnit.MILLISECONDS.toSeconds(AUTOMATIC_CHECK_OUT_RETRY_DELAY)))
                                .delay(AUTOMATIC_CHECK_OUT_RETRY_DELAY, TimeUnit.MILLISECONDS, Schedulers.io())));
    }

    public Completable disableAutomaticCheckOut() {
        return Maybe.fromCallable(() -> autoCheckoutGeofenceRequest)
                .flatMapCompletable(geofenceManager::removeGeofences)
                .doOnComplete(() -> autoCheckoutGeofenceRequest = null);
    }

    public Maybe<GeofencingRequest> getAutoCheckoutGeofenceRequest() {
        return Maybe.fromCallable(() -> autoCheckoutGeofenceRequest);
    }

    public Single<Boolean> isAutomaticCheckoutEnabled() {
        return getAutoCheckoutGeofenceRequest()
                .map(geofencingRequest -> true)
                .defaultIfEmpty(false);
    }

    private Completable performAutomaticCheckout() {
        return checkOut()
                .andThen(showAutomaticCheckoutNotification())
                .andThen(Completable.fromAction(() -> {
                    ((LucaApplication) context.getApplicationContext()).stopIfNotCurrentlyActive();
                }));
    }

    private Completable showAutomaticCheckoutNotification() {
        return Completable.fromAction(() -> {
            NotificationCompat.Builder notificationBuilder = notificationManager.createEventNotificationBuilder(
                    MainActivity.class,
                    R.string.notification_auto_checkout_triggered_title,
                    context.getString(R.string.notification_auto_checkout_triggered_description)
            );

            notificationManager.showNotification(NOTIFICATION_ID_EVENT, notificationBuilder.build())
                    .subscribe();
        });
    }

    private Single<GeofencingRequest> createAutoCheckoutGeofenceRequest() {
        return getCheckInDataIfAvailable()
                .toSingle()
                .flatMap(this::createGeofenceBuilder)
                .map(geofenceBuilder -> new GeofencingRequest.Builder()
                        .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_EXIT)
                        .addGeofence(geofenceBuilder.build())
                        .build())
                .doOnSuccess(geofencingRequest -> autoCheckoutGeofenceRequest = geofencingRequest);
    }

    private Single<Geofence.Builder> createGeofenceBuilder(@NonNull CheckInData checkInData) {
        return Single.defer(() -> {
            if (!checkInData.hasLocation()) {
                return Single.error(new GeofenceException("No location available for check-in data"));
            }

            long radius = checkInData.getRadius();
            radius = Math.max(MINIMUM_GEOFENCE_RADIUS, radius);
            radius = Math.min(MAXIMUM_GEOFENCE_RADIUS, radius);

            return Single.just(new Geofence.Builder()
                    .setRequestId(checkInData.getLocationId().toString().toLowerCase())
                    .setCircularRegion(checkInData.getLatitude(), checkInData.getLongitude(), radius)
                    .setNotificationResponsiveness((int) UPDATE_INTERVAL_DEFAULT)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT));
        });
    }

    /**
     * If currently checked in, this will poll the backend and check the check-in status. If the
     * status changes, this will trigger a checkout.
     */
    public Completable monitorCheckOutAtBackend() {
        return Observable.interval(0, CHECK_OUT_POLLING_INTERVAL, TimeUnit.MILLISECONDS, Schedulers.io())
                .flatMapCompletable(tick -> isCheckedIn()
                        .filter(Boolean::booleanValue)
                        .flatMapCompletable(isCheckedIn -> checkOutIfNotCheckedInAtBackend())
                        .doOnError(throwable -> Timber.w("Unable to monitor backend check-out: %s", throwable.toString()))
                        .onErrorComplete());
    }

    public Completable checkOutIfNotCheckedInAtBackend() {
        return isCheckedInAtBackend()
                .filter(isCheckedIn -> !isCheckedIn)
                .flatMapCompletable(isCheckedOut -> processCheckOut());
    }

    /*
        Distance and duration
     */

    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public Completable assertMinimumDistanceToLocation() {
        return getCheckInDataIfAvailable()
                .filter(CheckInData::hasLocationRestriction)
                .filter(data -> !skipMinimumDistanceAssertion)
                .flatMapCompletable(data -> {
                    if (!locationManager.hasLocationPermission()) {
                        return Completable.error(new CheckOutException("Location permission has not been granted", CheckOutException.MISSING_PERMISSION_ERROR));
                    } else if (!locationManager.isLocationServiceEnabled()) {
                        return Completable.error(new CheckOutException("Location service is disabled", CheckOutException.LOCATION_UNAVAILABLE_ERROR));
                    } else {
                        return getCurrentDistanceToVenueLocation()
                                .timeout(LOCATION_REQUEST_TIMEOUT, TimeUnit.MILLISECONDS)
                                .doOnError(throwable -> Timber.w(throwable, "Unable to get distance to venue location"))
                                .onErrorResumeNext(throwable -> Maybe.error(new CheckOutException("Unable to get location distance", throwable, CheckOutException.LOCATION_UNAVAILABLE_ERROR)))
                                .flatMapCompletable(distance -> {
                                    if (distance > data.getRadius()) {
                                        return Completable.complete();
                                    } else {
                                        return Completable.error(new CheckOutException("Current location still in venue range", CheckOutException.MINIMUM_DISTANCE_ERROR));
                                    }
                                });
                    }
                });
    }

    public Completable assertMinimumCheckInDuration() {
        return getCheckInDataIfAvailable()
                .filter(CheckInData::hasDurationRestriction)
                .filter(data -> !skipMinimumCheckInDurationAssertion)
                .flatMapCompletable(data -> getCurrentCheckInDuration()
                        .flatMapCompletable(checkInDuration -> {
                            if (checkInDuration > data.getMinimumDuration()) {
                                return Completable.complete();
                            } else {
                                return Completable.error(new CheckOutException("Minimum check-in duration not yet reached", CheckOutException.MINIMUM_DURATION_ERROR));
                            }
                        }));
    }

    public Maybe<Long> getCurrentCheckInDuration() {
        return getCheckInDataIfAvailable()
                .map(CheckInData::getTimestamp)
                .map(checkInTimestamp -> System.currentTimeMillis() - checkInTimestamp);
    }

    public Maybe<Location> getVenueLocation() {
        return getCheckInDataIfAvailable()
                .filter(CheckInData::hasLocation)
                .map(data -> {
                    Location venueLocation = new Location(android.location.LocationManager.GPS_PROVIDER);
                    venueLocation.setLatitude(data.getLatitude());
                    venueLocation.setLongitude(data.getLongitude());
                    return venueLocation;
                });
    }

    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public Maybe<Double> getCurrentDistanceToVenueLocation() {
        return getVenueLocation()
                .flatMap(location -> locationManager.getLastKnownDistanceTo(location)
                        .switchIfEmpty(locationManager.getDistanceUpdatesTo(location)
                                .firstElement()));
    }

    public Maybe<byte[]> getCheckedInTraceId() {
        return getCheckInDataIfAvailable()
                .map(CheckInData::getTraceId)
                .flatMapSingle(SerializationUtil::deserializeFromBase64);
    }

    /*
        Check-In Data
     */

    public Maybe<CheckInData> getCheckInDataIfAvailable() {
        return Maybe.fromCallable(() -> checkInData);
    }

    public Observable<CheckInData> getCheckInDataChanges() {
        return preferencesManager.restoreIfAvailableAndGetChanges(KEY_CHECK_IN_DATA, CheckInData.class)
                .doOnNext(checkInData -> Timber.v("Check-in data updated from preferences: %s", checkInData));
    }

    public Completable requestCheckInDataUpdates(long interval) {
        return pollCheckInData(interval)
                .distinctUntilChanged((previous, current) -> {
                    if (checkInData != null) {
                        return checkInData.getTraceId().equals(current.getTraceId());
                    } else {
                        return previous.getTraceId().equals(current.getTraceId());
                    }
                })
                .flatMapCompletable(this::processCheckIn)
                .doOnSubscribe(disposable -> Timber.d("Starting to request check-in data updates"))
                .doFinally(() -> Timber.d("Stopped requesting check-in data updates"));
    }

    private Observable<CheckInData> pollCheckInData(long interval) {
        return Observable.interval(0, interval, TimeUnit.MILLISECONDS, Schedulers.io())
                .flatMapMaybe(tick -> getCheckInDataFromBackend()
                        .doOnError(throwable -> Timber.w("Unable to get check-in data: %s", throwable.toString()))
                        .onErrorComplete());
    }

    private Maybe<CheckInData> getCheckInDataFromBackend() {
        return getTraceDataFromBackend()
                .flatMap(traceData -> {
                    if (traceData.isCheckedOut()) {
                        return Maybe.empty();
                    }
                    return networkManager.getLucaEndpoints().getLocation(traceData.getLocationId())
                            .map(location -> {
                                Timber.d("Creating check-in data for location: %s", location);
                                CheckInData checkInData = new CheckInData();
                                checkInData.setTraceId(traceData.getTraceId());
                                checkInData.setTimestamp(TimeUtil.convertFromUnixTimestamp(traceData.getCheckInTimestamp()).blockingGet());
                                checkInData.setLocationId(UUID.fromString(traceData.getLocationId()));
                                if (location.getGroupName() == null && location.getAreaName() == null) {
                                    // private meeting location
                                    if (meetingAdditionalData != null) {
                                        checkInData.setLocationAreaName(meetingAdditionalData.getFirstName() + " " + meetingAdditionalData.getLastName());
                                    }
                                    checkInData.setLocationGroupName(context.getString(R.string.meeting_heading));
                                } else {
                                    // regular location
                                    checkInData.setLocationGroupName(location.getGroupName());
                                    checkInData.setLocationAreaName(location.getAreaName());
                                    checkInData.setLatitude(location.getLatitude());
                                    checkInData.setLongitude(location.getLongitude());
                                }
                                checkInData.setRadius(location.getRadius());
                                checkInData.setMinimumDuration(MINIMUM_CHECK_IN_DURATION);
                                return checkInData;
                            }).toMaybe();

                })
                .doOnSubscribe(disposable -> Timber.d("Requesting check-in data from backend"));
    }

    private Completable persistCheckInData(CheckInData newCheckInData) {
        return preferencesManager.persist(KEY_CHECK_IN_DATA, newCheckInData);
    }

    private Completable removeCheckInDataIfCheckedOut() {
        return isCheckedInAtBackend()
                .flatMapCompletable(isCheckedIn -> {
                    if (!isCheckedIn) {
                        return removeCheckInData();
                    } else {
                        Timber.w("Not removing check-in data, still checked in");
                        return Completable.complete();
                    }
                });
    }

    private Completable removeCheckInData() {
        return Completable.mergeArray(
                cryptoManager.deleteTraceData(),
                preferencesManager.delete(KEY_CHECK_IN_DATA),
                preferencesManager.delete(KEY_CHECKED_IN_TRACE_ID),
                preferencesManager.delete(KEY_CHECKED_IN_VENUE_ID),
                preferencesManager.delete(KEY_CHECK_IN_TIMESTAMP)
        ).andThen(Completable.fromAction(() -> checkInData = null))
                .doOnComplete(() -> Timber.d("Removed check-in data"));
    }

    /*
        Trace data
     */

    private Maybe<TraceData> getTraceDataForCheckedInTraceIdFromBackend() {
        return getCheckedInTraceId()
                .flatMap(this::getTraceDataFromBackend);
    }

    private Maybe<TraceData> getTraceDataForRecentTraceIdsFromBackend() {
        return getRecentTraceIds()
                .takeLast(RECENT_TRACE_IDS_LIMIT)
                .toList()
                .flatMapObservable(this::getTraceDataFromBackend)
                .lastElement();
    }

    private Maybe<TraceData> getTraceDataFromBackend() {
        return getTraceDataForCheckedInTraceIdFromBackend()
                .switchIfEmpty(getTraceDataForRecentTraceIdsFromBackend());
    }

    private Maybe<TraceData> getTraceDataFromBackend(@NonNull byte[] traceId) {
        return Single.fromCallable(() -> Collections.singletonList(traceId))
                .flatMapObservable(this::getTraceDataFromBackend)
                .lastElement();
    }

    private Observable<TraceData> getTraceDataFromBackend(@NonNull List<byte[]> traceIds) {
        return Observable.fromIterable(traceIds)
                .flatMapSingle(SerializationUtil::serializeToBase64)
                .toList()
                .map(serializedTraceIds -> {
                    JsonArray jsonArray = new JsonArray(serializedTraceIds.size());
                    for (String serializedTraceId : serializedTraceIds) {
                        jsonArray.add(serializedTraceId);
                    }
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.add("traceIds", jsonArray);
                    return jsonObject;
                })
                .flatMap(jsonObject -> networkManager.getLucaEndpoints().getTraces(jsonObject))
                .flatMapObservable(Observable::fromIterable)
                .sorted((first, second) -> Long.compare(first.getCheckInTimestamp(), second.getCheckInTimestamp()));
    }

    private Observable<byte[]> getRecentTraceIds() {
        return cryptoManager.getTraceIdWrappers()
                .map(TraceIdWrapper::getTraceId);
    }

    public Observable<String> getArchivedTraceIds() {
        return getArchivedCheckInData()
                .map(CheckInData::getTraceId);
    }

    /*
        Archive
     */

    public Completable addCurrentCheckInDataToArchive() {
        return getCheckInDataIfAvailable()
                .flatMapCompletable(this::addCheckInDataToArchive);
    }

    public Completable addCheckInDataToArchive(@NonNull CheckInData checkInData) {
        return getArchivedCheckInData()
                .mergeWith(Observable.just(checkInData))
                .toList()
                .map(ArchivedCheckInData::new)
                .flatMapCompletable(archivedCheckInData -> preferencesManager.persist(KEY_ARCHIVED_CHECK_IN_DATA, archivedCheckInData))
                .doOnComplete(() -> Timber.i("Added check-in data to archive: %s", checkInData));
    }

    public Observable<CheckInData> getArchivedCheckInData() {
        return preferencesManager.restoreIfAvailable(KEY_ARCHIVED_CHECK_IN_DATA, ArchivedCheckInData.class)
                .map(ArchivedCheckInData::getCheckIns)
                .defaultIfEmpty(new ArrayList<>())
                .flatMapObservable(Observable::fromIterable);
    }

    public Maybe<CheckInData> getArchivedCheckInData(@NonNull String traceId) {
        return getArchivedCheckInData()
                .filter(archivedCheckInData -> traceId.equals(archivedCheckInData.getTraceId()))
                .firstElement();
    }

    public Completable deleteOldArchivedCheckInData() {
        return getArchivedCheckInData()
                .filter(checkInData -> checkInData.getTimestamp() > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14))
                .toList()
                .map(ArchivedCheckInData::new)
                .flatMapCompletable(archivedCheckInData -> preferencesManager.persist(KEY_ARCHIVED_CHECK_IN_DATA, archivedCheckInData))
                .doOnComplete(() -> Timber.d("Deleted old archived check-in data"));
    }

    public void setSkipMinimumCheckInDurationAssertion(boolean skipMinimumCheckInDurationAssertion) {
        this.skipMinimumCheckInDurationAssertion = skipMinimumCheckInDurationAssertion;
    }

    public void setSkipMinimumDistanceAssertion(boolean skipMinimumDistanceAssertion) {
        this.skipMinimumDistanceAssertion = skipMinimumDistanceAssertion;
    }

    @Nullable
    public MeetingAdditionalData getMeetingAdditionalData() {
        return meetingAdditionalData;
    }

    public void setMeetingAdditionalData(@Nullable MeetingAdditionalData meetingAdditionalData) {
        this.meetingAdditionalData = meetingAdditionalData;
    }

}
