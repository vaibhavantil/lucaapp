package de.culture4life.luca.ui;

import android.app.Application;

import com.tbruyelle.rxpermissions3.Permission;

import de.culture4life.luca.LucaApplication;
import de.culture4life.luca.R;
import de.culture4life.luca.notification.LucaNotificationManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.navigation.NavController;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import timber.log.Timber;

import static de.culture4life.luca.notification.LucaNotificationManager.NOTIFICATION_ID_EVENT;

public abstract class BaseViewModel extends AndroidViewModel {

    protected final LucaApplication application;
    protected final CompositeDisposable modelDisposable = new CompositeDisposable();
    protected final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();
    protected final MutableLiveData<Set<ViewError>> errors = new MutableLiveData<>();
    protected final MutableLiveData<ViewEvent<? extends Set<String>>> requiredPermissions = new MutableLiveData<>();
    protected NavController navigationController;

    public BaseViewModel(@NonNull Application application) {
        super(application);
        this.application = (LucaApplication) application;
        this.isLoading.setValue(false);
        this.errors.setValue(new HashSet<>());
        Timber.d("Created %s", this);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        modelDisposable.dispose();
    }

    @CallSuper
    public Completable initialize() {
        return updateRequiredPermissions()
                .doOnSubscribe(disposable -> Timber.d("Initializing %s", this));
    }

    @CallSuper
    public Completable keepDataUpdated() {
        return Completable.never();
    }

    protected final <ValueType> Completable update(@NonNull MutableLiveData<ValueType> mutableLiveData, ValueType value) {
        return Completable.fromAction(() -> updateAsSideEffect(mutableLiveData, value));
    }

    protected final <ValueType> void updateAsSideEffect(@NonNull MutableLiveData<ValueType> mutableLiveData, ValueType value) {
        mutableLiveData.postValue(value);
    }

    protected final Completable updateRequiredPermissions() {
        return createRequiredPermissions()
                .distinct()
                .toList()
                .map(HashSet::new)
                .map(ViewEvent::new)
                .flatMapCompletable(permissions -> update(requiredPermissions, permissions));
    }

    @CallSuper
    protected Observable<String> createRequiredPermissions() {
        return Observable.empty();
    }

    protected void addPermissionToRequiredPermissions(String... newlyRequiredPermissions) {
        addPermissionToRequiredPermissions(new HashSet<>(Arrays.asList(newlyRequiredPermissions)));
    }

    protected void addPermissionToRequiredPermissions(Set<String> newlyRequiredPermissions) {
        Timber.v("Added permissions to be requested: %s", newlyRequiredPermissions);
        ViewEvent<? extends Set<String>> permissions = this.requiredPermissions.getValue();
        if (permissions != null && !permissions.hasBeenHandled()) {
            newlyRequiredPermissions.addAll(permissions.getValueAndMarkAsHandled());
        }
        updateAsSideEffect(requiredPermissions, new ViewEvent<>(newlyRequiredPermissions));
    }

    /**
     * Will add the specified error to {@link #errors} when subscribed and remove it when disposed.
     */
    protected final Completable addErrorUntilDisposed(@NonNull ViewError viewError) {
        return Completable.create(emitter -> {
            addError(viewError);
            emitter.setCancellable(() -> removeError(viewError));
        });
    }

    protected ViewError.Builder createErrorBuilder(@NonNull Throwable throwable) {
        return new ViewError.Builder(application)
                .withCause(throwable);
    }

    protected final void addError(@Nullable ViewError viewError) {
        modelDisposable.add(Completable.fromAction(
                () -> {
                    if (viewError == null) {
                        return;
                    }
                    if (!application.isUiCurrentlyVisible() && viewError.canBeShownAsNotification()) {
                        showErrorAsNotification(viewError);
                    } else {
                        synchronized (errors) {
                            Set<ViewError> errorSet = new HashSet<>(errors.getValue());
                            errorSet.add(viewError);
                            errors.setValue(Collections.unmodifiableSet(errorSet));
                        }
                    }
                })
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> Timber.d("Added error: %s", viewError),
                        throwable -> Timber.w("Unable to add error: %s: %s", viewError, throwable.toString())
                ));
    }

    protected void showErrorAsNotification(@NonNull ViewError error) {
        LucaNotificationManager notificationManager = application.getNotificationManager();
        NotificationCompat.Builder notificationBuilder;
        if (error.isExpected()) {
            notificationBuilder = notificationManager
                    .createErrorNotificationBuilder(MainActivity.class, error.getTitle(), error.getDescription());
        } else {
            notificationBuilder = notificationManager
                    .createErrorNotificationBuilder(MainActivity.class, error.getTitle(), application.getString(R.string.error_specific_description, error.getDescription()));
        }

        notificationManager.showNotification(NOTIFICATION_ID_EVENT, notificationBuilder.build())
                .subscribe();
    }

    public final void removeError(@Nullable ViewError viewError) {
        modelDisposable.add(Completable.fromAction(
                () -> {
                    if (viewError == null) {
                        return;
                    }
                    synchronized (errors) {
                        Set<ViewError> errorSet = new HashSet<>(errors.getValue());
                        boolean removed = errorSet.remove(viewError);
                        if (removed) {
                            errors.setValue(Collections.unmodifiableSet(errorSet));
                        } else {
                            throw new IllegalStateException("Error was not added before");
                        }
                    }
                })
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> Timber.d("Removed error: %s", viewError),
                        throwable -> Timber.w("Unable to remove error: %s: %s", viewError, throwable.toString())
                ));
    }

    public void onErrorShown(@Nullable ViewError viewError) {
        Timber.d("onErrorShown() called with: viewError = [%s]", viewError);
        if (viewError.getRemoveWhenShown()) {
            removeError(viewError);
        }
    }

    public void onErrorDismissed(@Nullable ViewError viewError) {
        Timber.d("onErrorDismissed() called with: viewError = [%s]", viewError);
        removeError(viewError);
    }

    @CallSuper
    public void onPermissionResult(@NonNull Permission permission) {
        Timber.i("Permission result: %s", permission);
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public final LiveData<Set<ViewError>> getErrors() {
        return errors;
    }

    public final LiveData<ViewEvent<? extends Set<String>>> getRequiredPermissionsViewEvent() {
        return requiredPermissions;
    }

    public void setNavigationController(NavController navigationController) {
        this.navigationController = navigationController;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

}