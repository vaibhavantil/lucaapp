package de.culture4life.luca.ui.qrcode;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.mlkit.vision.barcode.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.zxing.EncodeHintType;

import android.annotation.SuppressLint;
import android.app.Application;
import android.graphics.Bitmap;
import android.webkit.URLUtil;

import de.culture4life.luca.R;
import de.culture4life.luca.checkin.CheckInData;
import de.culture4life.luca.checkin.CheckInManager;
import de.culture4life.luca.crypto.AsymmetricCipherProvider;
import de.culture4life.luca.crypto.DailyKeyPairPublicKeyWrapper;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.meeting.MeetingAdditionalData;
import de.culture4life.luca.meeting.MeetingManager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.notification.LucaNotificationManager;
import de.culture4life.luca.registration.RegistrationData;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.ui.BaseViewModel;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.util.SerializationUtil;
import de.culture4life.luca.util.TimeUtil;

import net.glxn.qrgen.android.QRCode;

import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

import static de.culture4life.luca.registration.RegistrationManager.USER_ID_KEY;

public class QrCodeViewModel extends BaseViewModel implements ImageAnalysis.Analyzer {

    private static final UUID DEBUGGING_SCANNER_ID = UUID.fromString("90e93809-2304-4e81-8c18-debf0a031c55");
    private static final long CHECK_IN_POLLING_INTERVAL = TimeUnit.SECONDS.toMillis(3);

    private final RegistrationManager registrationManager;
    private final CheckInManager checkInManager;
    private final CryptoManager cryptoManager;
    private final MeetingManager meetingManager;
    private final LucaNotificationManager notificationManager;

    private final MutableLiveData<Bitmap> qrCode = new MutableLiveData<>();
    private final MutableLiveData<String> name = new MutableLiveData<>();
    private final MutableLiveData<String> address = new MutableLiveData<>();
    private final MutableLiveData<String> phoneNumber = new MutableLiveData<>();
    private final MutableLiveData<CheckInData> checkInData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> networkAvailable = new MutableLiveData<>();
    private final MutableLiveData<Boolean> contactDataMissing = new MutableLiveData<>();
    private final MutableLiveData<Boolean> updateRequired = new MutableLiveData<>();
    private final MutableLiveData<String> privateMeetingUrl = new MutableLiveData<>();

    private final BarcodeScanner scanner;

    private UUID userId;
    private Disposable imageProcessingDisposable;
    private ViewError meetingError;
    private ViewError deepLinkError;

    public QrCodeViewModel(@NonNull Application application) {
        super(application);
        this.registrationManager = this.application.getRegistrationManager();
        this.checkInManager = this.application.getCheckInManager();
        this.cryptoManager = this.application.getCryptoManager();
        this.meetingManager = this.application.getMeetingManager();
        this.notificationManager = this.application.getNotificationManager();
        this.scanner = BarcodeScanning.getClient();
        this.isLoading.setValue(true);
    }

    @Override
    public Completable initialize() {
        return super.initialize()
                .andThen(Completable.fromAction(() -> checkInData.setValue(null)))
                .andThen(Completable.mergeArray(
                        registrationManager.initialize(application),
                        checkInManager.initialize(application),
                        cryptoManager.initialize(application),
                        meetingManager.initialize(application),
                        notificationManager.initialize(application)
                ))
                .andThen(application.getPreferencesManager().restore(USER_ID_KEY, UUID.class)
                        .doOnSuccess(uuid -> this.userId = uuid)
                        .ignoreElement())
                .doOnComplete(this::handleApplicationDeepLinkIfAvailable);
    }

    @Override
    public Completable keepDataUpdated() {
        return Completable.mergeArray(
                super.keepDataUpdated(),
                observeNetworkChanges(),
                observePreferenceChanges(),
                observeCheckInDataChanges(),
                keepUpdatingQrCodes().delaySubscription(100, TimeUnit.MILLISECONDS)
        );
    }

    private Completable observeNetworkChanges() {
        return Observable.interval(0, 1, TimeUnit.SECONDS)
                .flatMapSingle(tick -> application.getNetworkManager().isNetworkConnected())
                .flatMapCompletable(isNetworkConnected -> update(networkAvailable, isNetworkConnected));
    }

    private Completable observePreferenceChanges() {
        return Single.fromCallable(application::getPreferencesManager)
                .flatMapCompletable(preferencesManager -> Completable.mergeArray(
                        preferencesManager.restoreOrDefaultAndGetChanges(RegistrationManager.REGISTRATION_DATA_KEY, new RegistrationData())
                                .flatMapCompletable(registrationData ->
                                        Completable.mergeArray(
                                                update(name, registrationData.getFirstName() + " " + registrationData.getLastName()),
                                                update(address, registrationData.getStreet() + ", " + registrationData.getPostalCode() + " " + registrationData.getCity()),
                                                update(phoneNumber, registrationData.getPhoneNumber())
                                        ))
                ));
    }

    private Completable observeCheckInDataChanges() {
        return Completable.mergeArray(
                checkInManager.requestCheckInDataUpdates(CHECK_IN_POLLING_INTERVAL),
                checkInManager.getCheckInDataChanges()
                        .flatMapCompletable(updatedCheckInData -> Completable.fromAction(() -> {
                            updateAsSideEffect(checkInData, updatedCheckInData);
                            if (navigationController.getCurrentDestination().getId() == R.id.qrCodeFragment) {
                                navigationController.navigate(R.id.action_qrCodeFragment_to_venueDetailFragmentCheckedIn);
                            }
                        })));
    }

    public void checkIfContactDataMissing() {
        modelDisposable.add(registrationManager.hasProvidedRequiredContactData()
                .doOnSuccess(hasProvidedRequiredData -> Timber.v("Has provided required contact data: %b", hasProvidedRequiredData))
                .subscribeOn(Schedulers.io())
                .flatMapCompletable(hasProvidedRequiredData -> update(contactDataMissing, !hasProvidedRequiredData))
                .subscribe());
    }

    public void checkIfUpdateIsRequired() {
        modelDisposable.add(application.isUpdateRequired()
                .doOnSubscribe(disposable -> Timber.d("Checking if update is required"))
                .doOnSuccess(isUpdateRequired -> Timber.v("Update required: %b", isUpdateRequired))
                .doOnError(throwable -> Timber.w("Unable to check if update is required: %s", throwable.toString()))
                .flatMapCompletable(isUpdateRequired -> update(updateRequired, isUpdateRequired))
                .retryWhen(throwable -> throwable.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void checkIfHostingMeeting() {
        modelDisposable.add(meetingManager.isCurrentlyHostingMeeting()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        isHostingMeeting -> {
                            // TODO: 08.01.21 sometimes NullPointerException for getId() (on emulator)
                            int currentDestinationId = navigationController.getCurrentDestination().getId();
                            boolean isAtQrCodeFragment = currentDestinationId == R.id.qrCodeFragment;
                            if (isAtQrCodeFragment && isHostingMeeting) {
                                navigationController.navigate(R.id.action_qrCodeFragment_to_meetingFragment);
                            }
                        },
                        throwable -> Timber.w("Unable to check if hosting a meeting")
                ));
    }

    /*
        QR code generation
     */

    private Completable keepUpdatingQrCodes() {
        return Observable.interval(0, 1, TimeUnit.MINUTES, Schedulers.io())
                .flatMapCompletable(tick -> generateQrCodeData()
                        .doOnSubscribe(disposable -> Timber.d("Generating new QR code data"))
                        .doOnSuccess(qrCodeData -> Timber.i("Generated new QR code data: %s", qrCodeData))
                        .flatMap(this::serializeQrCodeData)
                        .doOnSuccess(serializedQrCodeData -> Timber.d("Serialized QR code data: %s", serializedQrCodeData))
                        .flatMap(this::generateQrCode)
                        .flatMapCompletable(bitmap -> update(qrCode, bitmap))
                        .doFinally(() -> updateAsSideEffect(isLoading, false)));
    }

    private Single<QrCodeData> generateQrCodeData() {
        return Single.just(new QrCodeData())
                .flatMap(qrCodeData -> cryptoManager.getTraceIdWrapper(userId)
                        .flatMapCompletable(userTraceIdWrapper -> Completable.mergeArray(
                                cryptoManager.getDailyKeyPairPublicKeyWrapper()
                                        .map(DailyKeyPairPublicKeyWrapper::getId)
                                        .doOnSuccess(qrCodeData::setKeyId)
                                        .ignoreElement(),
                                cryptoManager.getUserEphemeralKeyPair(userTraceIdWrapper.getTraceId())
                                        .observeOn(Schedulers.computation())
                                        .flatMapCompletable(keyPair -> Completable.mergeArray(
                                                encryptUserIdAndSecret(userId, keyPair)
                                                        .doOnSuccess(encryptedDataAndIv -> qrCodeData.setEncryptedData(encryptedDataAndIv.first))
                                                        .flatMap(encryptedDataAndIv -> generateVerificationTag(encryptedDataAndIv.first, userTraceIdWrapper.getTimestamp())
                                                                .doOnSuccess(qrCodeData::setVerificationTag))
                                                        .ignoreElement(),
                                                Single.just(keyPair.getPublic())
                                                        .cast(ECPublicKey.class)
                                                        .flatMap(publicKey -> AsymmetricCipherProvider.encode(publicKey, true))
                                                        .doOnSuccess(qrCodeData::setUserEphemeralPublicKey)
                                                        .ignoreElement()
                                        )),
                                TimeUtil.encodeUnixTimestamp(userTraceIdWrapper.getTimestamp())
                                        .doOnSuccess(qrCodeData::setTimestamp)
                                        .ignoreElement(),
                                Completable.fromAction(() -> qrCodeData.setTraceId(userTraceIdWrapper.getTraceId()))))
                        .andThen(Single.just(qrCodeData)));
    }

    private Single<android.util.Pair<byte[], byte[]>> encryptUserIdAndSecret(@NonNull UUID userId, @NonNull KeyPair userEphemeralKeyPair) {
        return Single.just(userEphemeralKeyPair.getPublic())
                .cast(ECPublicKey.class)
                .flatMap(publicKey -> AsymmetricCipherProvider.encode(publicKey, true))
                .flatMap(encodedPublicKey -> CryptoManager.trim(encodedPublicKey, 16))
                .flatMap(iv -> encryptUserIdAndSecret(userId, userEphemeralKeyPair.getPrivate(), iv)
                        .map(bytes -> new android.util.Pair<>(bytes, iv)));
    }

    private Single<byte[]> encryptUserIdAndSecret(@NonNull UUID userId, @NonNull PrivateKey userEphemeralPrivateKey, @NonNull byte[] iv) {
        return cryptoManager.getDataSecret()
                .flatMap(userDataSecret -> CryptoManager.encode(userId)
                        .flatMap(encodedUserId -> CryptoManager.concatenate(encodedUserId, userDataSecret)))
                .flatMap(encodedData -> cryptoManager.generateEphemeralDiffieHellmanSecret(userEphemeralPrivateKey)
                        .flatMap(cryptoManager::generateDataEncryptionSecret)
                        .flatMap(CryptoManager::createKeyFromSecret)
                        .flatMap(encodingKey -> cryptoManager.getSymmetricCipherProvider().encrypt(encodedData, iv, encodingKey)));
    }

    private Single<byte[]> generateVerificationTag(@NonNull byte[] encryptedUserIdAndSecret, long roundedUnixTimestamp) {
        return TimeUtil.encodeUnixTimestamp(roundedUnixTimestamp)
                .flatMap(encodedTimestamp -> CryptoManager.concatenate(encodedTimestamp, encryptedUserIdAndSecret))
                .flatMap(encodedData -> cryptoManager.getDataSecret()
                        .flatMap(cryptoManager::generateDataAuthenticationSecret)
                        .flatMap(CryptoManager::createKeyFromSecret)
                        .flatMap(dataAuthenticationKey -> cryptoManager.getMacProvider().sign(encodedData, dataAuthenticationKey)))
                .flatMap(verificationTag -> CryptoManager.trim(verificationTag, 8))
                .doOnSuccess(verificationTag -> Timber.d("Generated new verification tag: %s", SerializationUtil.serializeToBase64(verificationTag).blockingGet()));
    }

    private Single<String> serializeQrCodeData(@NonNull QrCodeData qrCodeData) {
        return Single.fromCallable(() -> ByteBuffer.allocate(96)
                .put(qrCodeData.getVersion())
                .put(qrCodeData.getDeviceType())
                .put(qrCodeData.getKeyId())
                .put(qrCodeData.getTimestamp())
                .put(qrCodeData.getTraceId())
                .put(qrCodeData.getEncryptedData())
                .put(qrCodeData.getUserEphemeralPublicKey())
                .put(qrCodeData.getVerificationTag())
                .array())
                .flatMap(encodedQrCodeData -> cryptoManager.getHashProvider().hash(encodedQrCodeData)
                        .flatMap(checksum -> CryptoManager.trim(checksum, 4))
                        .flatMap(checksum -> CryptoManager.concatenate(encodedQrCodeData, checksum)))
                .flatMap(SerializationUtil::serializeToZ85);
    }

    private Single<Bitmap> generateQrCode(@NonNull String data) {
        return Single.fromCallable(() -> QRCode.from(data)
                .withSize(500, 500)
                .withHint(EncodeHintType.MARGIN, 0)
                .bitmap());
    }

    /*
        QR code scanning
     */

    @SuppressLint("UnsafeExperimentalUsageError")
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        if (imageProcessingDisposable != null && !imageProcessingDisposable.isDisposed()) {
            Timber.v("Not processing new camera image, still processing previous one");
            imageProxy.close();
            return;
        }

        imageProcessingDisposable = processCameraImage(imageProxy)
                .doOnError(throwable -> Timber.w("Unable to process camera image: %s", throwable.toString()))
                .onErrorComplete()
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(imageProxy::close)
                .subscribeOn(Schedulers.computation())
                .subscribe();

        modelDisposable.add(imageProcessingDisposable);
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private Completable processCameraImage(@NonNull ImageProxy imageProxy) {
        return Maybe.fromCallable(imageProxy::getImage)
                .filter(image -> {
                    if (deepLinkError != null && errors.getValue().contains(deepLinkError)) {
                        // currently showing a deep-link related error
                        return false;
                    } else if (privateMeetingUrl.getValue() != null) {
                        // currently joining private meeting
                        return false;
                    } else {
                        return true;
                    }
                })
                .map(image -> InputImage.fromMediaImage(image, imageProxy.getImageInfo().getRotationDegrees()))
                .flatMapObservable(this::detectBarcodes)
                .flatMapCompletable(this::processBarcode);
    }

    private Observable<Barcode> detectBarcodes(@NonNull InputImage image) {
        return Observable.create(emitter -> scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        emitter.onNext(barcode);
                    }
                    emitter.onComplete();
                })
                .addOnFailureListener(emitter::tryOnError));
    }

    private Completable processBarcode(@NonNull Barcode barcode) {
        return Maybe.fromCallable(barcode::getRawValue)
                .doOnSuccess(value -> Timber.d("Processing barcode: %s", value))
                .filter(QrCodeViewModel::isDeepLink)
                .doOnSuccess(deepLink -> notificationManager.vibrate().subscribe())
                .flatMapCompletable(this::handleDeepLink);
    }

    /*
        Deep link handling
     */

    private void handleApplicationDeepLinkIfAvailable() {
        modelDisposable.add(application.getDeepLink()
                .flatMapCompletable(url -> handleDeepLink(url)
                        .doOnComplete(() -> application.onDeepLinkHandled(url)))
                .subscribe(
                        () -> Timber.d("Handled application deep link"),
                        throwable -> Timber.w("Unable handle application deep link: %s", throwable.toString())
                ));
    }

    private Completable handleDeepLink(@NonNull String url) {
        return Completable.defer(() -> {
            if (url.contains("/meeting")) {
                return handleMeetingCheckInDeepLink(url);
            } else {
                return handleSelfCheckInDeepLink(url);
            }
        })
                .doOnSubscribe(disposable -> removeError(deepLinkError))
                .doOnError(throwable -> {
                    deepLinkError = createErrorBuilder(throwable)
                            .withTitle(R.string.error_check_in_failed)
                            .withResolveAction(handleDeepLink(url))
                            .withResolveLabel(R.string.action_retry)
                            .build();
                    addError(deepLinkError);
                });
    }

    private Completable handleMeetingCheckInDeepLink(@NonNull String url) {
        return update(privateMeetingUrl, url);
    }

    private Completable handleMeetingCheckInDeepLinkAfterApproval(@NonNull String url) {
        Completable extractMeetingHostName = getMeetingAdditionalDataFromUrl(url)
                .doOnSuccess(checkInManager::setMeetingAdditionalData)
                .ignoreElement();

        Single<UUID> scannerId = getScannerIdFromUrl(url);
        Single<String> additionalData = application.getRegistrationManager()
                .getOrCreateRegistrationData()
                .map(MeetingAdditionalData::new)
                .map(meetingAdditionalData -> new Gson().toJson(meetingAdditionalData));

        return extractMeetingHostName.andThen(Single.zip(scannerId, additionalData, Pair::new))
                .flatMapCompletable(scannerIdAndAdditionalData -> performSelfCheckIn(scannerIdAndAdditionalData.first, scannerIdAndAdditionalData.second));
    }

    private Single<MeetingAdditionalData> getMeetingAdditionalDataFromUrl(@NonNull String url) {
        return getAdditionalFromUrlIfAvailable(url)
                .toSingle()
                .map(json -> new Gson().fromJson(json, MeetingAdditionalData.class));
    }

    private Completable handleSelfCheckInDeepLink(@NonNull String url) {
        Single<UUID> scannerId = getScannerIdFromUrl(url);
        Single<String> additionalData = getAdditionalFromUrlIfAvailable(url).defaultIfEmpty("");

        return Single.zip(scannerId, additionalData, Pair::new)
                .flatMapCompletable(scannerIdAndAdditionalData -> performSelfCheckIn(scannerIdAndAdditionalData.first, scannerIdAndAdditionalData.second));
    }

    private Completable performSelfCheckIn(UUID scannerId, @Nullable String additionalData) {
        return generateQrCodeData()
                .flatMapCompletable(qrCodeData -> checkInManager.checkIn(scannerId, qrCodeData))
                .doOnComplete(() -> uploadAdditionalDataIfAvailableAsSideEffect(scannerId, additionalData));
    }

    private void uploadAdditionalDataIfAvailableAsSideEffect(@NonNull UUID scannerId, @Nullable String additionalData) {
        uploadAdditionalDataIfAvailable(scannerId, additionalData)
                .doOnError(throwable -> Timber.w("Unable to upload additional data: %s", throwable.toString()))
                .retryWhen(errors -> errors.delay(10, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.v("Uploaded additional data"),
                        throwable -> Timber.e(throwable, "Unable to upload additional data")
                );
    }

    private Completable uploadAdditionalDataIfAvailable(@NonNull UUID scannerId, @Nullable String additionalData) {
        return Maybe.fromCallable(() -> additionalData)
                .filter(data -> !data.isEmpty())
                .map(JsonParser::parseString)
                .map(JsonElement::getAsJsonObject)
                .flatMapCompletable(additionalProperties -> uploadAdditionalData(scannerId, additionalProperties));
    }

    private Completable uploadAdditionalData(@NonNull UUID scannerId, @NonNull JsonObject additionalData) {
        return checkInManager.getLocationPublicKey(scannerId)
                .flatMapCompletable(locationPublicKey -> checkInManager.addAdditionalCheckInProperties(additionalData, locationPublicKey));
    }

    public void onDebuggingCheckInRequested() {
        modelDisposable.add(generateQrCodeData()
                .flatMapCompletable(qrCodeData -> checkInManager.checkIn(DEBUGGING_SCANNER_ID, qrCodeData))
                .doOnSubscribe(disposable -> updateAsSideEffect(isLoading, true))
                .doFinally(() -> updateAsSideEffect(isLoading, false))
                .subscribe(
                        () -> Timber.i("Checked in"),
                        throwable -> Timber.w("Unable to check in: %s", throwable.toString())
                ));
    }

    public void onPrivateMeetingJoinApproved(@NonNull String url) {
        modelDisposable.add(handleMeetingCheckInDeepLinkAfterApproval(url)
                .doOnSubscribe(disposable -> updateAsSideEffect(privateMeetingUrl, null))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(disposable -> {
                    updateAsSideEffect(isLoading, true);
                    removeError(meetingError);
                })
                .doOnError(throwable -> {
                    ViewError.Builder errorBuilder = createErrorBuilder(throwable)
                            .withTitle(R.string.error_check_in_failed)
                            .removeWhenShown();

                    if (NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_NOT_FOUND)) {
                        errorBuilder.withDescription(R.string.error_location_not_found);
                    }

                    meetingError = errorBuilder.build();
                    addError(meetingError);
                })
                .doFinally(() -> updateAsSideEffect(isLoading, false))
                .subscribe(
                        () -> Timber.i("Joined private meeting"),
                        throwable -> Timber.w("Unable to join private meeting: %s", throwable.toString())
                ));
    }

    public void onPrivateMeetingJoinDismissed(@NonNull String url) {
        updateAsSideEffect(privateMeetingUrl, null);
    }

    public void onPrivateMeetingCreationRequested() {
        modelDisposable.add(createMeeting()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            Timber.i("Meeting created");
                            navigationController.navigate(R.id.action_qrCodeFragment_to_meetingFragment);
                        },
                        throwable -> Timber.w("Unable to create meeting: %s", throwable.toString())
                ));
    }

    private Completable createMeeting() {
        return meetingManager.createPrivateMeeting()
                .doOnSubscribe(disposable -> {
                    Timber.d("Creating meeting");
                    updateAsSideEffect(isLoading, true);
                    removeError(meetingError);
                })
                .doOnError(throwable -> {
                    meetingError = createErrorBuilder(throwable)
                            .withTitle(R.string.error_request_failed_title)
                            .removeWhenShown()
                            .build();
                    addError(meetingError);
                })
                .doFinally(() -> updateAsSideEffect(isLoading, false));
    }

    private static Single<UUID> getScannerIdFromUrl(@NonNull String url) {
        return Single.fromCallable(() -> {
            int startIndex = url.lastIndexOf('/');
            int endIndex = url.indexOf('#');
            if (startIndex < 0 || endIndex < 0) {
                throw new IllegalArgumentException("Unable to get scanner ID from URL: " + url);
            }
            return UUID.fromString(url.substring(startIndex + 1, endIndex));
        });
    }

    private static Maybe<String> getAdditionalFromUrlIfAvailable(@NonNull String url) {
        return Maybe.fromCallable(
                () -> {
                    int index = url.indexOf('#');
                    if (index < 0) {
                        return null;
                    }
                    return url.substring(index + 1);
                })
                .flatMapSingle(SerializationUtil::deserializeFromBase64)
                .map(String::new);
    }

    private static boolean isDeepLink(@NonNull String data) {
        return URLUtil.isHttpsUrl(data) && data.contains("luca-app.de");
    }

    public LiveData<Bitmap> getQrCode() {
        return qrCode;
    }

    public LiveData<String> getName() {
        return name;
    }

    public LiveData<String> getAddress() {
        return address;
    }

    public LiveData<String> getPhoneNumber() {
        return phoneNumber;
    }

    public LiveData<CheckInData> getCheckInData() {
        return checkInData;
    }

    public LiveData<Boolean> isNetworkAvailable() {
        return networkAvailable;
    }

    public LiveData<Boolean> isUpdateRequired() {
        return updateRequired;
    }

    public LiveData<Boolean> isContactDataMissing() {
        return contactDataMissing;
    }

    public LiveData<String> getPrivateMeetingUrl() {
        return privateMeetingUrl;
    }

}
