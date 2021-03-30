package de.culture4life.luca.meeting;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import android.content.Context;

import de.culture4life.luca.Manager;
import de.culture4life.luca.crypto.AsymmetricCipherProvider;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.location.LocationManager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.pojo.TracesResponseData;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.util.SerializationUtil;
import de.culture4life.luca.util.TimeUtil;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import timber.log.Timber;

public class MeetingManager extends Manager {

    public static final String KEY_CURRENT_MEETING_DATA = "current_meeting_data";
    public static final String KEY_ARCHIVED_MEETING_DATA = "archived_meeting_data";

    private final PreferencesManager preferencesManager;
    private final NetworkManager networkManager;
    private final LocationManager locationManager;
    private final CryptoManager cryptoManager;
    private final HistoryManager historyManager;

    @Nullable
    private MeetingData currentMeetingData;

    public MeetingManager(@NonNull PreferencesManager preferencesManager, @NonNull NetworkManager networkManager, @NonNull LocationManager locationManager, @NonNull HistoryManager historyManager, @NonNull CryptoManager cryptoManager) {
        this.preferencesManager = preferencesManager;
        this.networkManager = networkManager;
        this.locationManager = locationManager;
        this.historyManager = historyManager;
        this.cryptoManager = cryptoManager;
    }

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return Completable.mergeArray(
                preferencesManager.initialize(context),
                networkManager.initialize(context),
                locationManager.initialize(context),
                historyManager.initialize(context),
                cryptoManager.initialize(context)
        ).andThen(Completable.mergeArray(
                restoreCurrentMeetingDataIfAvailable().ignoreElement(),
                deleteOldArchivedMeetingData()
        ));
    }

    public Observable<Boolean> getMeetingHostStateChanges() {
        return Observable.interval(1, TimeUnit.SECONDS)
                .flatMapSingle(tick -> isCurrentlyHostingMeeting())
                .distinctUntilChanged();
    }

    public Single<Boolean> isCurrentlyHostingMeeting() {
        return getCurrentMeetingDataIfAvailable()
                .switchIfEmpty(restoreCurrentMeetingDataIfAvailable())
                .isEmpty()
                .map(noMeetingDataAvailable -> !noMeetingDataAvailable);
    }

    public Maybe<MeetingData> getCurrentMeetingDataIfAvailable() {
        return Maybe.fromCallable(() -> currentMeetingData);
    }

    public Maybe<MeetingData> restoreCurrentMeetingDataIfAvailable() {
        return preferencesManager.restoreIfAvailable(KEY_CURRENT_MEETING_DATA, MeetingData.class)
                .doOnSuccess(meetingData -> this.currentMeetingData = meetingData);
    }

    public Completable persistCurrentMeetingData(@NonNull MeetingData meetingData) {
        return preferencesManager.persist(KEY_CURRENT_MEETING_DATA, meetingData);
    }

    /*
        Start
     */

    public Completable createPrivateMeeting() {
        return cryptoManager.generateMeetingEphemeralKeyPair()
                .flatMapCompletable(keyPair -> createPrivateLocation((ECPublicKey) keyPair.getPublic())
                        .doOnSuccess(meetingData -> {
                            Timber.i("Created meeting data: %s", meetingData);
                            this.currentMeetingData = meetingData;
                        })
                        .flatMapCompletable(meetingData -> Completable.mergeArray(
                                persistCurrentMeetingData(meetingData),
                                cryptoManager.persistMeetingEphemeralKeyPair(meetingData.getLocationId(), keyPair)
                        ).andThen(historyManager.addMeetingStartedItem(meetingData))));
    }

    private Single<MeetingData> createPrivateLocation(@NonNull ECPublicKey publicKey) {
        return AsymmetricCipherProvider.encode(publicKey)
                .flatMap(SerializationUtil::serializeToBase64)
                .map(serializedPubKey -> {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("publicKey", serializedPubKey);
                    return jsonObject;
                })
                .flatMap(requestData -> networkManager.getLucaEndpoints().createPrivateLocation(requestData))
                .map(MeetingData::new);
    }

    /*
        End
     */

    public Completable closePrivateLocation() {
        return restoreCurrentMeetingDataIfAvailable()
                .flatMapCompletable(meetingData -> networkManager.getLucaEndpoints()
                        .closePrivateLocation(meetingData.getAccessId().toString())
                        .onErrorResumeNext(throwable -> {
                            if (NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_NOT_FOUND)) {
                                // meeting has already ended
                                return Completable.complete();
                            }
                            return Completable.error(throwable);
                        })
                        .andThen(addMeetingToHistory(meetingData))
                        .andThen(addCurrentMeetingDataToArchive()));
    }

    private Completable addMeetingToHistory(@NonNull MeetingData meetingData) {
        return historyManager.addMeetingEndedItem(meetingData);
    }

    /*
        Status
     */

    public Completable updateMeetingGuestData() {
        return fetchGuestData()
                .flatMapSingle(this::getMeetingGuestData)
                .toList()
                .flatMapCompletable(meetingGuestData -> getCurrentMeetingDataIfAvailable()
                        .doOnSuccess(meetingData -> meetingData.setGuestData(meetingGuestData))
                        .flatMapCompletable(this::persistCurrentMeetingData));
    }

    public Observable<TracesResponseData> fetchGuestData() {
        return restoreCurrentMeetingDataIfAvailable()
                .map(MeetingData::getAccessId)
                .flatMapSingle(accessUuid -> networkManager.getLucaEndpoints().fetchGuestList(accessUuid.toString()))
                .doOnSuccess(tracesResponseData -> Timber.d("Location traces: %s", tracesResponseData))
                .flatMapObservable(Observable::fromIterable);
    }

    private Single<MeetingGuestData> getMeetingGuestData(@NonNull TracesResponseData tracesResponseData) {
        return Single.fromCallable(() -> {
            MeetingGuestData meetingGuestData = new MeetingGuestData();

            meetingGuestData.setTraceId(tracesResponseData.getTraceId());

            try {
                TracesResponseData.AdditionalData additionalData = tracesResponseData.getAdditionalData();
                if (additionalData == null) {
                    throw new IllegalStateException("No additional data available for " + tracesResponseData.getTraceId());
                }

                UUID meetingId = getCurrentMeetingDataIfAvailable()
                        .toSingle()
                        .map(MeetingData::getLocationId)
                        .blockingGet();

                PrivateKey meetingPrivateKey = cryptoManager.getMeetingEphemeralPrivateKey(meetingId).blockingGet();
                PublicKey guestPublicKey = SerializationUtil.deserializeFromBase64(additionalData.getPublicKey())
                        .flatMap(AsymmetricCipherProvider::decodePublicKey)
                        .blockingGet();

                byte[] diffieHellmanSecret = cryptoManager.getAsymmetricCipherProvider()
                        .generateSecret(meetingPrivateKey, guestPublicKey)
                        .blockingGet();

                byte[] encryptedData = SerializationUtil.deserializeFromBase64(additionalData.getData()).blockingGet();
                byte[] iv = SerializationUtil.deserializeFromBase64(additionalData.getIv()).blockingGet();
                byte[] encryptionSecret = cryptoManager.generateDataEncryptionSecret(diffieHellmanSecret).blockingGet();
                SecretKey decryptionKey = CryptoManager.createKeyFromSecret(encryptionSecret).blockingGet();
                byte[] decryptedData = cryptoManager.getSymmetricCipherProvider().decrypt(encryptedData, iv, decryptionKey).blockingGet();

                byte[] dataAuthenticationSecret = cryptoManager.generateDataAuthenticationSecret(diffieHellmanSecret).blockingGet();
                SecretKey dataAuthenticationKey = CryptoManager.createKeyFromSecret(dataAuthenticationSecret).blockingGet();
                byte[] mac = SerializationUtil.deserializeFromBase64(additionalData.getMac()).blockingGet();
                cryptoManager.getMacProvider().verify(encryptedData, mac, dataAuthenticationKey).blockingAwait();

                MeetingAdditionalData meetingAdditionalData = Single.fromCallable(() -> new String(decryptedData, StandardCharsets.UTF_8))
                        .doOnSuccess(json -> Timber.d("Additional data JSON: %s", json))
                        .map(json -> new Gson().fromJson(json, MeetingAdditionalData.class))
                        .blockingGet();

                meetingGuestData.setFirstName(meetingAdditionalData.getFirstName());
                meetingGuestData.setLastName(meetingAdditionalData.getLastName());
            } catch (Exception e) {
                Timber.w("Unable to extract guest names from additional data: %s", e.toString());
            }

            long checkInTimestamp = TimeUtil.convertFromUnixTimestamp(tracesResponseData.getCheckInTimestamp()).blockingGet();
            meetingGuestData.setCheckInTimestamp(checkInTimestamp);

            long checkOutTimestamp = TimeUtil.convertFromUnixTimestamp(tracesResponseData.getCheckOutTimestampOrZero()).blockingGet();
            meetingGuestData.setCheckOutTimestamp(checkOutTimestamp);

            return meetingGuestData;
        });
    }

    /*
        Archive
     */

    public Completable addCurrentMeetingDataToArchive() {
        return restoreCurrentMeetingDataIfAvailable()
                .flatMapCompletable(this::addMeetingDataToArchive)
                .andThen(preferencesManager.delete(KEY_CURRENT_MEETING_DATA))
                .doOnComplete(() -> this.currentMeetingData = null);
    }

    public Completable addMeetingDataToArchive(@NonNull MeetingData meetingData) {
        return getArchivedMeetingData()
                .mergeWith(Observable.just(meetingData))
                .toList()
                .map(ArchivedMeetingData::new)
                .flatMapCompletable(meetingsDataArchive -> preferencesManager.persist(KEY_ARCHIVED_MEETING_DATA, meetingsDataArchive))
                .doOnComplete(() -> Timber.i("Added meeting data to archive: %s", meetingData));
    }

    public Observable<MeetingData> getArchivedMeetingData() {
        return preferencesManager.restoreIfAvailable(KEY_ARCHIVED_MEETING_DATA, ArchivedMeetingData.class)
                .map(ArchivedMeetingData::getMeetings)
                .defaultIfEmpty(new ArrayList<>())
                .flatMapObservable(Observable::fromIterable);
    }

    public Completable deleteOldArchivedMeetingData() {
        return getArchivedMeetingData()
                .filter(meetingData -> meetingData.getCreationTimestamp() > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14))
                .toList()
                .map(ArchivedMeetingData::new)
                .flatMapCompletable(meetingsDataArchive -> preferencesManager.persist(KEY_ARCHIVED_MEETING_DATA, meetingsDataArchive))
                .doOnComplete(() -> Timber.d("Deleted old archived meeting data"));
    }

    public static String getReadableGuestName(@NonNull MeetingGuestData guestData) {
        String name;
        if (guestData.getFirstName() != null) {
            name = guestData.getFirstName() + " " + guestData.getLastName();
        } else {
            // this can happen if the guest already checked in but
            // hasn't uploaded the additional data (containing the name) yet
            name = "Trace ID " + guestData.getTraceId().substring(0, 8);
        }
        return name;
    }

}
