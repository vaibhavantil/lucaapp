package de.culture4life.luca.ui.meeting;

import com.google.gson.Gson;
import com.google.zxing.EncodeHintType;

import android.app.Application;
import android.graphics.Bitmap;
import android.util.Pair;

import de.culture4life.luca.BuildConfig;
import de.culture4life.luca.R;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.meeting.MeetingAdditionalData;
import de.culture4life.luca.meeting.MeetingData;
import de.culture4life.luca.meeting.MeetingGuestData;
import de.culture4life.luca.meeting.MeetingManager;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.ui.BaseViewModel;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.ui.venue.details.VenueDetailsViewModel;
import de.culture4life.luca.util.SerializationUtil;

import net.glxn.qrgen.android.QRCode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class MeetingViewModel extends BaseViewModel {

    private final RegistrationManager registrationManager;
    private final MeetingManager meetingManager;
    private final CryptoManager cryptoManager;

    private final MutableLiveData<Boolean> isHostingMeeting = new MutableLiveData<>();
    private final MutableLiveData<Bitmap> qrCode = new MutableLiveData<>();
    private final MutableLiveData<String> duration = new MutableLiveData<>();
    private final MutableLiveData<String> membersCount = new MutableLiveData<>();
    private final MutableLiveData<String> checkedInMemberNames = new MutableLiveData<>();
    private final MutableLiveData<String> checkedOutMemberNames = new MutableLiveData<>();

    private long meetingCreationTimestamp;

    @Nullable
    private ViewError meetingError;

    public MeetingViewModel(@NonNull Application application) {
        super(application);
        this.registrationManager = this.application.getRegistrationManager();
        this.meetingManager = this.application.getMeetingManager();
        this.cryptoManager = this.application.getCryptoManager();
        membersCount.setValue("0/0");
        checkedInMemberNames.setValue("");
        checkedOutMemberNames.setValue("");
        meetingCreationTimestamp = System.currentTimeMillis();
    }

    @Override
    public Completable initialize() {
        return super.initialize()
                .andThen(Completable.mergeArray(
                        registrationManager.initialize(application),
                        meetingManager.initialize(application),
                        cryptoManager.initialize(application)
                ))
                .andThen(meetingManager.isCurrentlyHostingMeeting()
                        .flatMapCompletable(hosting -> update(isHostingMeeting, hosting)));
    }

    @Override
    public Completable keepDataUpdated() {
        return Completable.mergeArray(
                super.keepDataUpdated(),
                keepUpdatingMeetingData(),
                keepUpdatingMeetingDuration(),
                keepUpdatingQrCodes().delaySubscription(100, TimeUnit.MILLISECONDS)
        );
    }

    private Completable keepUpdatingMeetingDuration() {
        return Observable.interval(0, 1, TimeUnit.SECONDS)
                .map(tick -> System.currentTimeMillis() - meetingCreationTimestamp)
                .map(VenueDetailsViewModel::getReadableDuration)
                .flatMapCompletable(readableDuration -> update(duration, readableDuration));
    }

    private Completable keepUpdatingMeetingData() {
        return Observable.interval(0, 5, TimeUnit.SECONDS, Schedulers.io())
                .flatMapCompletable(tick -> meetingManager.updateMeetingGuestData()
                        .andThen(updateGuests())
                        .doOnError(throwable -> Timber.w("Unable to update members: %s", throwable.toString()))
                        .onErrorComplete());
    }

    private Completable updateGuests() {
        return meetingManager.getCurrentMeetingDataIfAvailable()
                .flatMapCompletable(meetingData -> Completable.fromAction(() -> {
                    List<String> checkedInList = new ArrayList<>();
                    List<String> checkedOutList = new ArrayList<>();

                    for (MeetingGuestData guestData : meetingData.getGuestData()) {
                        String name = MeetingManager.getReadableGuestName(guestData);
                        boolean isCheckedOut = guestData.getCheckOutTimestamp() > 0 && guestData.getCheckOutTimestamp() < System.currentTimeMillis();
                        if (isCheckedOut) {
                            checkedOutList.add(name);
                        } else {
                            checkedInList.add(name);
                        }
                    }

                    updateAsSideEffect(checkedInMemberNames, HistoryManager.createUnorderedList(checkedInList));
                    updateAsSideEffect(checkedOutMemberNames, HistoryManager.createUnorderedList(checkedOutList));
                    updateAsSideEffect(membersCount, checkedInList.size() + "/" + meetingData.getGuestData().size());
                    meetingCreationTimestamp = meetingData.getCreationTimestamp();
                }));
    }

    private Completable keepUpdatingQrCodes() {
        return Observable.interval(0, 1, TimeUnit.MINUTES, Schedulers.io())
                .flatMapCompletable(tick -> generateQrCodeData()
                        .doOnSubscribe(disposable -> updateAsSideEffect(isLoading, true))
                        .doOnSuccess(qrCodeData -> Timber.i("Generated new QR code data: %s", qrCodeData))
                        .flatMap(this::generateQrCode)
                        .flatMapCompletable(bitmap -> update(qrCode, bitmap))
                        .doFinally(() -> updateAsSideEffect(isLoading, false)));
    }

    private Single<String> generateQrCodeData() {
        Single<UUID> scannerId = meetingManager.restoreCurrentMeetingDataIfAvailable()
                .toSingle()
                .map(MeetingData::getScannerId);

        Single<String> additionalData = registrationManager.getOrCreateRegistrationData()
                .map(MeetingAdditionalData::new)
                .map(meetingAdditionalData -> new Gson().toJson(meetingAdditionalData))
                .map(json -> json.getBytes(StandardCharsets.UTF_8))
                .flatMap(SerializationUtil::serializeToBase64);

        return Single.zip(scannerId, additionalData, Pair::new)
                .flatMap(meetingIdAndData -> generateQrCodeData(meetingIdAndData.first, meetingIdAndData.second));
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    private Single<String> generateQrCodeData(@NonNull UUID scannerId, @NonNull String additionalData) {
        return Single.fromCallable(() -> new StringBuilder()
                .append("https://")
                .append(BuildConfig.DEBUG ? "staging" : "app")
                .append(".luca-app.de/webapp/meeting/")
                .append(scannerId)
                .append("#")
                .append(additionalData)
                .toString());
    }

    private Single<Bitmap> generateQrCode(@NonNull String url) {
        return Single.fromCallable(() -> QRCode.from(url)
                .withSize(500, 500)
                .withHint(EncodeHintType.MARGIN, 0)
                .bitmap());
    }

    public void onMeetingEndRequested() {
        modelDisposable.add(endMeeting()
                .subscribeOn(Schedulers.io())
                .onErrorComplete()
                .subscribe());
    }

    private Completable endMeeting() {
        return meetingManager.closePrivateLocation()
                .doOnSubscribe(disposable -> {
                    Timber.d("Ending meeting");
                    updateAsSideEffect(isLoading, true);
                    removeError(meetingError);
                })
                .doOnComplete(() -> updateAsSideEffect(isHostingMeeting, false))
                .doOnError(throwable -> {
                    Timber.w("Unable to end meeting: %s", throwable.toString());
                    meetingError = createErrorBuilder(throwable)
                            .withTitle(R.string.error_request_failed_title)
                            .removeWhenShown()
                            .build();
                    addError(meetingError);
                })
                .doFinally(() -> updateAsSideEffect(isLoading, false));
    }

    public LiveData<Boolean> getIsHostingMeeting() {
        return isHostingMeeting;
    }

    public LiveData<Bitmap> getQrCode() {
        return qrCode;
    }

    public LiveData<String> getDuration() {
        return duration;
    }

    public LiveData<String> getMembersCount() {
        return membersCount;
    }

    public LiveData<String> getCheckedInMemberNames() {
        return checkedInMemberNames;
    }

    public LiveData<String> getCheckedOutMemberNames() {
        return checkedOutMemberNames;
    }

}
