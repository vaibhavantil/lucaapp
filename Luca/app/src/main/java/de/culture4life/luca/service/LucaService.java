package de.culture4life.luca.service;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

import de.culture4life.luca.LucaApplication;
import de.culture4life.luca.R;
import de.culture4life.luca.checkin.CheckInData;
import de.culture4life.luca.checkin.CheckInManager;
import de.culture4life.luca.meeting.MeetingManager;
import de.culture4life.luca.notification.LucaNotificationManager;
import de.culture4life.luca.ui.MainActivity;

import java.util.concurrent.TimeUnit;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class LucaService extends Service {

    private LucaApplication application;

    private LucaNotificationManager notificationManager;
    private CheckInManager checkInManager;
    private MeetingManager meetingManager;

    private CompositeDisposable serviceDisposable;

    @Override
    public void onCreate() {
        super.onCreate();
        application = (LucaApplication) getApplication();
        notificationManager = application.getNotificationManager();
        checkInManager = application.getCheckInManager();
        meetingManager = application.getMeetingManager();

        serviceDisposable = new CompositeDisposable();
        initializeBlocking().blockingAwait(10, TimeUnit.SECONDS);
        promoteToForeground();
    }

    /**
     * Initializes everything that is required during service creation.
     */
    @CallSuper
    private Completable initializeBlocking() {
        return Completable.mergeArray(
                notificationManager.initialize(this),
                checkInManager.initialize(this),
                meetingManager.initialize(this)
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LucaNotificationManager.getBundleFromIntent(intent)
                .flatMap(LucaNotificationManager::getActionFromBundle)
                .flatMapCompletable(this::processNotificationAction)
                .subscribe(
                        () -> Timber.d("Processed notification action"),
                        throwable -> Timber.w(throwable, "Unable to process notification action")
                );

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        serviceDisposable.dispose();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void promoteToForeground() {
        Notification foregroundNotification;
        if (checkInManager.isCheckedIn().blockingGet()) {
            foregroundNotification = createCheckedInNotification();
        } else if (meetingManager.isCurrentlyHostingMeeting().blockingGet()) {
            foregroundNotification = createMeetingHostNotification();
        } else {
            Timber.w("Unable to promote service to foreground. Device is currently not checked in.");
            stopSelf();
            return;
        }
        startForeground(LucaNotificationManager.NOTIFICATION_ID_STATUS, foregroundNotification);
    }

    private Notification createCheckedInNotification() {
        return notificationManager.createCheckedInNotificationBuilder(MainActivity.class)
                .setContentTitle(checkInManager.getCheckInDataIfAvailable()
                        .toSingle()
                        .map(CheckInData::getLocationDisplayName)
                        .map(venueName -> getString(R.string.notification_checked_in_at_title, venueName))
                        .onErrorReturnItem(getString(R.string.notification_service_title))
                        .blockingGet())
                .build();
    }

    private Notification createMeetingHostNotification() {
        return notificationManager.createMeetingHostNotificationBuilder(MainActivity.class)
                .build();
    }

    private Completable processNotificationAction(int action) {
        return Completable.fromAction(() -> {
            switch (action) {
                case LucaNotificationManager.ACTION_STOP:
                    stopSelf();
                    application.stop();
                    break;
                case LucaNotificationManager.ACTION_CHECKOUT:
                    serviceDisposable.add(checkInManager.checkOut()
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .doOnSubscribe(disposable -> showStatus(R.string.status_check_out_in_progress))
                            .doOnComplete(() -> showStatus(R.string.status_check_out_succeeded))
                            .doOnError(throwable -> showStatus(R.string.status_check_out_failed))
                            .subscribe(
                                    () -> {
                                        Timber.i("Checkout succeeded");
                                        application.stopIfNotCurrentlyActive();
                                    },
                                    throwable -> {
                                        Timber.w("Checkout failed: %s", throwable.toString());
                                        openApp();
                                    }
                            ));
                    break;
                case LucaNotificationManager.ACTION_END_MEETING:
                    serviceDisposable.add(meetingManager.closePrivateLocation()
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    () -> {
                                        Timber.i("Meeting end succeeded");
                                        application.stopIfNotCurrentlyActive();
                                    },
                                    throwable -> {
                                        Timber.w("Meeting end failed: %s", throwable.toString());
                                        openApp();
                                    }
                            ));
                    break;
                default:
                    Timber.w("Unknown notification action: %d", action);
            }
        });
    }

    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void showStatus(@StringRes int stringResource) {
        try {
            Toast.makeText(this, stringResource, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Timber.w("Unable to show status: %s", e.toString());
        }
    }

}
