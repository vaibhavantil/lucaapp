package de.culture4life.luca.ui.venue.details;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Build;

import com.tbruyelle.rxpermissions3.Permission;

import de.culture4life.luca.R;
import de.culture4life.luca.checkin.CheckInData;
import de.culture4life.luca.checkin.CheckInManager;
import de.culture4life.luca.checkin.CheckOutException;
import de.culture4life.luca.location.GeofenceManager;
import de.culture4life.luca.location.LocationManager;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.ui.BaseViewModel;
import de.culture4life.luca.ui.ViewError;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

import static de.culture4life.luca.checkin.CheckInManager.KEY_CHECK_IN_DATA;

public class VenueDetailsViewModel extends BaseViewModel {

    private final SimpleDateFormat readableDateFormat;

    private final PreferencesManager preferenceManager;
    private final CheckInManager checkInManager;
    private final GeofenceManager geofenceManager;
    private final LocationManager locationManager;

    private final MutableLiveData<UUID> id = new MutableLiveData<>();
    private final MutableLiveData<String> title = new MutableLiveData<>();
    private final MutableLiveData<String> description = new MutableLiveData<>();
    private final MutableLiveData<String> checkInTime = new MutableLiveData<>();
    private final MutableLiveData<String> checkInDuration = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isCheckedIn = new MutableLiveData<>();
    private final MutableLiveData<Boolean> hasLocationRestriction = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldEnableAutomaticCheckOut = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldEnableLocationServices = new MutableLiveData<>();
    private final MutableLiveData<Boolean> shouldShowLocationAccessDialog = new MutableLiveData<>();

    private boolean isLocationPermissionGranted = false;
    private boolean isBackgroundLocationPermissionGranted = false;

    @Nullable
    private ViewError checkOutError;

    public VenueDetailsViewModel(@NonNull Application application) {
        super(application);
        preferenceManager = this.application.getPreferencesManager();
        checkInManager = this.application.getCheckInManager();
        geofenceManager = this.application.getGeofenceManager();
        locationManager = this.application.getLocationManager();

        readableDateFormat = new SimpleDateFormat(application.getString(R.string.venue_checked_in_time_format), Locale.GERMANY);
        isCheckedIn.setValue(false);
    }

    @Override
    public Completable initialize() {
        return super.initialize()
                .andThen(Completable.mergeArray(
                        preferenceManager.initialize(application),
                        checkInManager.initialize(application),
                        geofenceManager.initialize(application),
                        locationManager.initialize(application)
                ))
                .andThen(initializeAutomaticCheckout())
                .andThen(checkInManager.isCheckedIn()
                        .flatMapCompletable(checkedIn -> update(isCheckedIn, checkedIn)))
                .andThen(checkInManager.getCheckInDataIfAvailable()
                        .doOnSuccess(checkInData -> {
                            updateAsSideEffect(id, checkInData.getLocationId());
                            updateAsSideEffect(title, checkInData.getLocationDisplayName());
                            updateAsSideEffect(hasLocationRestriction, checkInData.hasLocationRestriction());
                        })
                        .ignoreElement());
    }

    private Completable initializeAutomaticCheckout() {
        return Completable.mergeArray(
                Single.fromCallable(locationManager::hasLocationPermission)
                        .flatMapCompletable(hasLocationAccess -> update(shouldShowLocationAccessDialog, !hasLocationAccess)),
                checkInManager.isAutomaticCheckoutEnabled()
                        .flatMapCompletable(automaticCheckoutEnabled -> Completable.defer(() -> {
                            if (automaticCheckoutEnabled) {
                                return startObservingAutomaticCheckOutErrors();
                            } else {
                                return Completable.complete();
                            }
                        }).andThen(update(shouldEnableAutomaticCheckOut, automaticCheckoutEnabled)))
        );
    }

    private Completable startObservingAutomaticCheckOutErrors() {
        return Completable.fromAction(() -> modelDisposable.add(checkInManager.getAutoCheckoutGeofenceRequest()
                .flatMapObservable(geofencingRequest -> Observable.fromIterable(geofencingRequest.getGeofences()))
                .flatMap(geofenceManager::getGeofenceEvents)
                .ignoreElements()
                .onErrorResumeNext(this::handleAutomaticCheckOutError)
                .subscribeOn(Schedulers.io())
                .subscribe()));
    }

    @Override
    public Completable keepDataUpdated() {
        return Completable.mergeArray(
                super.keepDataUpdated(),
                keepCheckedInStateUpdated(),
                keepCheckedInTimerUpdated()
        );
    }

    private Completable keepCheckedInStateUpdated() {
        return checkInManager.getCheckedInStateChanges()
                .flatMapCompletable(checkedInState -> update(isCheckedIn, checkedInState));
    }

    private Completable keepCheckedInTimerUpdated() {
        return preferenceManager.restoreIfAvailableAndGetChanges(KEY_CHECK_IN_DATA, CheckInData.class)
                .map(CheckInData::getTimestamp)
                .switchMapCompletable(checkInTimestamp -> Completable.mergeArray(
                        updateDescription(checkInTimestamp),
                        updateReadableCheckInTime(checkInTimestamp),
                        updateReadableCheckInDuration(checkInTimestamp)
                ));
    }

    private Completable updateReadableCheckInTime(long timestamp) {
        return Single.fromCallable(() -> application.getString(R.string.venue_checked_in_time, getReadableTime(timestamp)))
                .flatMapCompletable(readableTime -> update(checkInTime, readableTime));
    }

    private Completable updateDescription(long timestamp) {
        return Single.fromCallable(() -> application.getString(R.string.venue_description, getReadableTime(timestamp)))
                .flatMapCompletable(updatedDescription -> update(description, updatedDescription));
    }

    private Completable updateReadableCheckInDuration(long timestamp) {
        return Observable.interval(0, 1, TimeUnit.SECONDS)
                .map(tick -> System.currentTimeMillis() - timestamp)
                .map(VenueDetailsViewModel::getReadableDuration)
                .flatMapCompletable(readableDuration -> update(checkInDuration, readableDuration));
    }

    public void onSlideCompleted() {
        if (isCheckedIn.getValue()) {
            onCheckOutRequested();
        } else {
            onCheckInRequested();
        }
    }

    /**
     * Manual check in request by the user. As he should already be checked in, this is not
     * implemented.
     */
    public void onCheckInRequested() {
        modelDisposable.add(Completable.error(new RuntimeException("Not implemented"))
                .doOnSubscribe(disposable -> updateAsSideEffect(isLoading, true))
                .doOnComplete(() -> updateAsSideEffect(isCheckedIn, true))
                .doFinally(() -> updateAsSideEffect(isLoading, false))
                .subscribe(
                        () -> Timber.i("Checked in"),
                        throwable -> Timber.w("Unable to check in: %s", throwable.toString())
                ));
    }

    /**
     * Manual check out by the user.
     */
    public void onCheckOutRequested() {
        modelDisposable.add(checkInManager.checkOut()
                .doOnSubscribe(disposable -> {
                    updateAsSideEffect(isLoading, true);
                    removeError(checkOutError);
                })
                .doOnComplete(() -> updateAsSideEffect(isCheckedIn, false))
                .doFinally(() -> updateAsSideEffect(isLoading, false))
                .doOnError(throwable -> {
                    if (throwable instanceof CheckOutException) {
                        int error = ((CheckOutException) throwable).getErrorCode();
                        if (error == CheckOutException.LOCATION_UNAVAILABLE_ERROR ||
                                error == CheckOutException.MISSING_PERMISSION_ERROR) {
                            checkInManager.setSkipMinimumDistanceAssertion(true);
                        }
                    }
                    checkOutError = createCheckOutViewError(throwable).build();
                    addError(checkOutError);
                })
                .subscribe(
                        () -> Timber.i("Checked out"),
                        throwable -> Timber.w("Unable to check out: %s", throwable.toString())
                ));
    }

    /**
     * Check if all conditions for activating automatic check-out are met, if not try to enable them
     * one by one.
     *
     * @return {@code true} if permissions are granted and location is active, {@code false}
     *         otherwise.
     */
    private boolean enableAutomaticCheckoutActivation() {
        if (ActivityCompat.checkSelfPermission(application, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && ActivityCompat.checkSelfPermission(application, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestBackgroundLocationPermission();
            return false;
        }
        if (!isLocationServiceEnabled()) {
            updateAsSideEffect(shouldEnableLocationServices, true);
            return false;
        }
        return true;
    }

    private boolean canEnableAutomaticCheckoutActivation() {
        if (ActivityCompat.checkSelfPermission(application, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateAsSideEffect(shouldShowLocationAccessDialog, true);
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && ActivityCompat.checkSelfPermission(application, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateAsSideEffect(shouldShowLocationAccessDialog, true);
            return false;
        }
        return isLocationServiceEnabled();
    }

    /**
     * Enable automatic check out if all conditions are met.
     */
    @SuppressLint("MissingPermission")
    public void enableAutomaticCheckout() {
        if (checkInManager.isAutomaticCheckoutEnabled().blockingGet() || !enableAutomaticCheckoutActivation()) {
            return;
        }
        modelDisposable.add(checkInManager.enableAutomaticCheckOut()
                .andThen(startObservingAutomaticCheckOutErrors())
                .andThen(Completable.mergeArray(
                        update(shouldEnableAutomaticCheckOut, true),
                        update(shouldShowLocationAccessDialog, false)
                ))
                .doOnError(throwable -> {
                    onEnablingAutomaticCheckOutFailed();
                    modelDisposable.add(handleAutomaticCheckOutError(throwable)
                            .onErrorComplete()
                            .subscribe());
                })
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.v("Automatic check-out was enabled"),
                        throwable -> Timber.w(throwable, "Unable to enable automatic check-out")
                ));
    }

    public void disableAutomaticCheckout() {
        modelDisposable.add(checkInManager.disableAutomaticCheckOut()
                .andThen(update(shouldEnableAutomaticCheckOut, false))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.v("Automatic check-out was disabled"),
                        throwable -> Timber.w(throwable, "Unable to disable automatic check-out")
                ));
    }

    private Completable handleAutomaticCheckOutError(@NonNull Throwable throwable) {
        return Completable.fromAction(() -> {
            ViewError.Builder errorBuilder = new ViewError.Builder(application)
                    .withTitle(R.string.auto_checkout_generic_error_title)
                    .withCause(throwable)
                    .removeWhenShown()
                    .canBeShownAsNotification();

            if (!locationManager.isLocationServiceEnabled()) {
                errorBuilder.isExpected()
                        .withTitle(R.string.auto_checkout_location_disabled_title)
                        .withDescription(R.string.auto_checkout_location_disabled_description);
            }

            addError(errorBuilder.build());
            disableAutomaticCheckout();
        });
    }

    /*
        Permission handling
     */

    @Override
    public void onPermissionResult(@NonNull Permission permission) {
        super.onPermissionResult(permission);
        if (permission.granted) {
            onPermissionGranted(permission);
        } else {
            onPermissionDenied(permission);
        }
    }

    @SuppressLint("InlinedApi")
    private void onPermissionGranted(@NonNull Permission permission) {
        switch (permission.name) {
            case Manifest.permission.ACCESS_FINE_LOCATION: {
                if (!isLocationPermissionGranted) {
                    onLocationPermissionGranted();
                    isLocationPermissionGranted = true;
                }
                break;
            }
            case Manifest.permission.ACCESS_BACKGROUND_LOCATION: {
                if (!isBackgroundLocationPermissionGranted) {
                    onBackgroundLocationPermissionGranted();
                    isBackgroundLocationPermissionGranted = true;
                }
                break;
            }
        }
    }

    /**
     * Handles granting of the Manifest.permission.ACCESS_FINE_LOCATION permission.
     */
    private void onLocationPermissionGranted() {
        updateAsSideEffect(shouldEnableAutomaticCheckOut, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackgroundLocationPermission();
        } else {
            enableAutomaticCheckout();
        }
    }

    private void onBackgroundLocationPermissionGranted() {
        updateAsSideEffect(shouldEnableAutomaticCheckOut, true);
        enableAutomaticCheckout();
    }

    public void onPermissionDenied(@NonNull Permission permission) {
        onPermissionDenied(permission, permission.shouldShowRequestPermissionRationale);
    }

    public void onPermissionDenied(@NonNull Permission permission, boolean shouldShowRationale) {
        switch (permission.name) {
            case Manifest.permission.ACCESS_FINE_LOCATION: {
                onLocationPermissionDenied(shouldShowRationale);
                break;
            }
            case Manifest.permission.ACCESS_BACKGROUND_LOCATION: {
                onBackgroundLocationPermissionDenied(shouldShowRationale);
                break;
            }
        }
    }

    private void onLocationPermissionDenied(boolean shouldShowRationale) {
        onEnablingAutomaticCheckOutFailed();
    }

    private void onBackgroundLocationPermissionDenied(boolean shouldShowRationale) {
        onLocationPermissionDenied(shouldShowRationale);
    }

    void onEnablingAutomaticCheckOutFailed() {
        updateAsSideEffect(shouldEnableAutomaticCheckOut, false);
    }

    private ViewError.Builder createCheckOutViewError(@NonNull Throwable throwable) {
        ViewError.Builder builder = createErrorBuilder(throwable)
                .withTitle(R.string.venue_check_out_error);

        if (throwable instanceof CheckOutException) {
            switch (((CheckOutException) throwable).getErrorCode()) {
                case CheckOutException.MISSING_PERMISSION_ERROR: {
                    builder.withDescription(R.string.venue_check_out_error_permission)
                            .withResolveAction(Completable.fromAction(this::requestLocationPermission))
                            .withResolveLabel(R.string.action_grant);
                    break;
                }
                case CheckOutException.MINIMUM_DURATION_ERROR: {
                    builder.withDescription(R.string.venue_check_out_error_duration);
                    break;
                }
                case CheckOutException.MINIMUM_DISTANCE_ERROR: {
                    builder.withDescription(R.string.venue_check_out_error_in_range);
                    break;
                }
                case CheckOutException.LOCATION_UNAVAILABLE_ERROR: {
                    builder.withDescription(R.string.venue_check_out_error_location_unavailable);
                    break;
                }
                default: {
                    builder.withDescription(R.string.error_generic_title);
                    break;
                }
            }
        }

        return builder;
    }

    public void requestLocationPermissionForAutomaticCheckOut() {
        requestLocationPermission();
    }

    private void requestLocationPermission() {
        addPermissionToRequiredPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void requestBackgroundLocationPermissionForAutomaticCheckOut() {
        requestBackgroundLocationPermission();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void requestBackgroundLocationPermission() {
        addPermissionToRequiredPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    /*
        Getter & Setter
    */

    public boolean isLocationServiceEnabled() {
        return locationManager.isLocationServiceEnabled();
    }

    public LiveData<String> getTitle() {
        return title;
    }

    public LiveData<String> getDescription() {
        return description;
    }

    public LiveData<String> getCheckInTime() {
        return checkInTime;
    }

    public LiveData<String> getCheckInDuration() {
        return checkInDuration;
    }

    public LiveData<Boolean> getIsCheckedIn() {
        return isCheckedIn;
    }

    public MutableLiveData<Boolean> getHasLocationRestriction() {
        return hasLocationRestriction;
    }

    public MutableLiveData<Boolean> getShouldEnableAutomaticCheckOut() {
        return shouldEnableAutomaticCheckOut;
    }

    public MutableLiveData<Boolean> getShouldEnableLocationServices() {
        return shouldEnableLocationServices;
    }

    public MutableLiveData<Boolean> getShouldShowLocationAccessDialog() {
        return shouldShowLocationAccessDialog;
    }

    public static String getReadableDuration(long duration) {
        long seconds = Math.abs(duration / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        seconds = (seconds % 3600) % 60;
        return String.format(Locale.GERMANY, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String getReadableTime(long timestamp) {
        return readableDateFormat.format(new Date(timestamp));
    }

}
