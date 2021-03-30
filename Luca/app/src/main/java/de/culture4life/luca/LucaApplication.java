package de.culture4life.luca;

import com.google.android.gms.instantapps.InstantApps;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import de.culture4life.luca.checkin.CheckInManager;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.dataaccess.DataAccessManager;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.location.GeofenceManager;
import de.culture4life.luca.location.LocationManager;
import de.culture4life.luca.meeting.MeetingManager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.notification.LucaNotificationManager;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.service.LucaService;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.ui.dialog.BaseDialogFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLPeerUnverifiedException;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;
import hu.akarnokd.rxjava3.debug.RxJavaAssemblyTracking;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import rxdogtag2.RxDogTag;
import timber.log.Timber;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class LucaApplication extends MultiDexApplication {

    private final PreferencesManager preferencesManager;
    private final CryptoManager cryptoManager;
    private final NetworkManager networkManager;
    private final LucaNotificationManager notificationManager;
    private final LocationManager locationManager;
    private final RegistrationManager registrationManager;
    private final CheckInManager checkInManager;
    private final MeetingManager meetingManager;
    private final HistoryManager historyManager;
    private final DataAccessManager dataAccessManager;
    private final GeofenceManager geofenceManager;

    private final CompositeDisposable applicationDisposable;

    private final Set<Activity> startedActivities;

    @Nullable
    private String deepLink;

    public LucaApplication() {
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
            RxJavaAssemblyTracking.enable();
        }

        preferencesManager = new PreferencesManager();
        notificationManager = new LucaNotificationManager();
        locationManager = new LocationManager();
        networkManager = new NetworkManager();
        geofenceManager = new GeofenceManager();
        historyManager = new HistoryManager(preferencesManager);
        cryptoManager = new CryptoManager(preferencesManager, networkManager);
        registrationManager = new RegistrationManager(preferencesManager, networkManager, cryptoManager);
        meetingManager = new MeetingManager(preferencesManager, networkManager, locationManager, historyManager, cryptoManager);
        checkInManager = new CheckInManager(preferencesManager, networkManager, geofenceManager, locationManager, historyManager, cryptoManager, notificationManager);
        dataAccessManager = new DataAccessManager(preferencesManager, networkManager, notificationManager, checkInManager, historyManager, cryptoManager);

        applicationDisposable = new CompositeDisposable();

        startedActivities = new HashSet<>();

        setupErrorHandler();
    }

    private void setupErrorHandler() {
        RxDogTag.install();
        RxJavaPlugins.setErrorHandler(throwable -> {
            if (throwable instanceof UndeliverableException) {
                // This may happen when race conditions cause multiple errors to be emitted.
                // As only one error can be handled by the stream, subsequent errors are undeliverable.
                // See https://github.com/ReactiveX/RxJava/issues/7008
                Timber.w(throwable.getCause(), "Undeliverable exception");
            } else {
                Timber.e(throwable, "Unhandled error");
                // forward the error, most likely crashing the app
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), throwable);
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.d("Creating application");
        if (!isRunningUnitTests()) {
            initializeBlocking().blockingAwait(10, TimeUnit.SECONDS);
            initializeAsync().subscribeOn(Schedulers.io()).subscribe();
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
        Timber.d("Application created");
    }

    /**
     * Initializes everything that is required during application creation.
     */
    @CallSuper
    private Completable initializeBlocking() {
        return Completable.mergeArray(
                preferencesManager.initialize(this)
        );
    }

    /**
     * Initializes everything that is not required instantly after application creation.
     */
    @CallSuper
    private Completable initializeAsync() {
        return Completable.mergeArray(
                notificationManager.initialize(this).subscribeOn(Schedulers.io()),
                networkManager.initialize(this).subscribeOn(Schedulers.io()),
                cryptoManager.initialize(this).subscribeOn(Schedulers.io()),
                locationManager.initialize(this).subscribeOn(Schedulers.io()),
                registrationManager.initialize(this).subscribeOn(Schedulers.io()),
                checkInManager.initialize(this).subscribeOn(Schedulers.io()),
                historyManager.initialize(this).subscribeOn(Schedulers.io()),
                dataAccessManager.initialize(this).subscribeOn(Schedulers.io()),
                geofenceManager.initialize(this).subscribeOn(Schedulers.io())
        ).andThen(Completable.mergeArray(
                invokeRotatingBackendPublicKeyUpdate(),
                invokeAccessedDataUpdate(),
                startKeepingDataUpdated()
        ));
    }

    private Completable invokeRotatingBackendPublicKeyUpdate() {
        return Completable.fromAction(() -> applicationDisposable.add(cryptoManager.updateDailyKeyPairPublicKey()
                .doOnError(throwable -> {
                    if (throwable instanceof SSLPeerUnverifiedException) {
                        showErrorAsDialog(new ViewError.Builder(this)
                                .withTitle(R.string.error_certificate_pinning_title)
                                .withDescription(R.string.error_certificate_pinning_description)
                                .withCause(throwable)
                                .removeWhenShown()
                                .build());
                    }
                })
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.d("Updated rotating backend public key"),
                        throwable -> Timber.w("Unable to update rotating backend public key: %s", throwable.toString())
                )));
    }

    private Completable invokeAccessedDataUpdate() {
        return Completable.fromAction(() -> applicationDisposable.add(dataAccessManager.update()
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.i("Updated accessed data"),
                        throwable -> Timber.w("Unable to update accessed data: %s", throwable.toString())
                )));
    }

    private Completable startKeepingDataUpdated() {
        return Completable.fromAction(() -> applicationDisposable.add(keepDataUpdated()
                .subscribeOn(Schedulers.io())
                .doOnError(throwable -> Timber.w("Unable to keep data updated: %s", throwable.toString()))
                .retryWhen(error -> error.delay(3, TimeUnit.SECONDS))
                .subscribe()));
    }

    private Completable keepDataUpdated() {
        return Completable.mergeArray(
                monitorCheckedInState(),
                monitorMeetingHostState(),
                checkInManager.monitorCheckOutAtBackend()
        );
    }

    private Completable monitorCheckedInState() {
        return checkInManager.getCheckedInStateChanges()
                .flatMapCompletable(isCheckedIn -> startOrStopServiceIfRequired());
    }

    private Completable monitorMeetingHostState() {
        return meetingManager.getMeetingHostStateChanges()
                .flatMapCompletable(isHostingMeeting -> startOrStopServiceIfRequired());
    }

    private Completable startOrStopServiceIfRequired() {
        return Single.zip(checkInManager.isCheckedIn(), meetingManager.isCurrentlyHostingMeeting(),
                (isCheckedIn, isHostingMeeting) -> isCheckedIn || isHostingMeeting)
                .flatMapCompletable(shouldStartService -> Completable.fromAction(() -> {
                    if (shouldStartService) {
                        startService();
                    } else {
                        stopService();
                    }
                }));
    }

    public void startService() {
        Intent intent = new Intent(this, LucaService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    public void stopService() {
        Intent intent = new Intent(this, LucaService.class);
        stopService(intent);
    }

    public void stopIfNotCurrentlyActive() {
        if (!isUiCurrentlyVisible()) {
            stop();
        }
    }

    /**
     * Will exit the current process, effectively stopping the service and the UI.
     */
    @CallSuper
    public void stop() {
        applicationDisposable.dispose();
        dataAccessManager.dispose();
        checkInManager.dispose();
        registrationManager.dispose();
        cryptoManager.dispose();
        historyManager.dispose();
        networkManager.dispose();
        locationManager.dispose();
        notificationManager.dispose();
        preferencesManager.dispose();
        geofenceManager.dispose();
        stopService();
        Timber.i("Stopping application");
        System.exit(0);
    }

    public void openUrl(@NonNull String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        Context context = getActivityContext();
        if (context == null) {
            context = this;
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public void openAppSettings() {
        Intent intent = new Intent();
        // https://stackoverflow.com/questions/31127116/open-app-permission-settings:
        // This does not work for third party app permission solutions, such as used in the
        // oneplus 2 oxygen os. Settings are managed from a custom settings view in oxygen os
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null);
        intent.setData(uri);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public Completable handleDeepLink(Uri uri) {
        return Completable.fromAction(() -> deepLink = uri.toString());
    }

    public boolean isInDarkMode() {
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public boolean isInstantApp() {
        return InstantApps.getPackageManagerCompat(this).isInstantApp();
    }

    public boolean isUiCurrentlyVisible() {
        return !startedActivities.isEmpty();
    }

    public Single<Boolean> isUpdateRequired() {
        return networkManager.getLucaEndpoints()
                .getSupportedVersionNumber()
                .map(jsonObject -> jsonObject.get("minimumVersion").getAsInt())
                .doOnSuccess(versionNumber -> Timber.d("Minimum supported app version number: %d", versionNumber))
                .map(minimumVersionNumber -> BuildConfig.VERSION_CODE < minimumVersionNumber);
    }

    public void onActivityStarted(@NonNull Activity activity) {
        startedActivities.add(activity);
    }

    public void onActivityStopped(@NonNull Activity activity) {
        startedActivities.remove(activity);
    }

    protected void showErrorAsDialog(@NonNull ViewError error) {
        Activity activity = getActivityContext();
        if (activity == null) {
            Timber.w("Unable to show error, no started activity available: %s", error);
            return;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(error.getTitle())
                .setMessage(error.getDescription());

        if (error.isResolvable()) {
            builder.setPositiveButton(error.getResolveLabel(), (dialog, which) -> applicationDisposable.add(error.getResolveAction()
                    .subscribe(
                            () -> Timber.d("Error resolved"),
                            throwable -> Timber.w("Unable to resolve error: %s", throwable.toString())
                    )));
        } else {
            builder.setPositiveButton(R.string.action_ok, (dialog, which) -> {
                // do nothing
            });
        }

        new BaseDialogFragment(builder).show();
    }

    @Nullable
    private Activity getActivityContext() {
        if (startedActivities.isEmpty()) {
            return null;
        }
        return new ArrayList<>(startedActivities).get(0);
    }

    public static boolean isRunningUnitTests() {
        try {
            Class.forName("de.culture4life.luca.LucaUnitTest");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public PreferencesManager getPreferencesManager() {
        return preferencesManager;
    }

    public CryptoManager getCryptoManager() {
        return cryptoManager;
    }

    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    public LucaNotificationManager getNotificationManager() {
        return notificationManager;
    }

    public LocationManager getLocationManager() {
        return locationManager;
    }

    public RegistrationManager getRegistrationManager() {
        return registrationManager;
    }

    public CheckInManager getCheckInManager() {
        return checkInManager;
    }

    public MeetingManager getMeetingManager() {
        return meetingManager;
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    public DataAccessManager getDataAccessManager() {
        return dataAccessManager;
    }

    public GeofenceManager getGeofenceManager() {
        return geofenceManager;
    }

    public Maybe<String> getDeepLink() {
        return Maybe.fromCallable(() -> deepLink);
    }

    public void onDeepLinkHandled(@NonNull String url) {
        deepLink = null;
    }

}
