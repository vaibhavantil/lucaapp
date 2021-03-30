package de.culture4life.luca;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import timber.log.Timber;

/**
 * Base class for managers that require initialization. Allows multiple concurrent subscriptions to
 * {@link #initialize(Context)} but will make sure that {@link #doInitialize(Context)} is only done
 * once.
 */
public abstract class Manager {

    private boolean isInitializing;
    private boolean isInitialized;

    protected Context context;

    protected CompositeDisposable managerDisposable;

    @CallSuper
    public Completable initialize(@NonNull Context context) {
        return Completable.defer(() -> {
            Completable initializationCompletable;
            synchronized (this) {
                if (isInitialized) {
                    Timber.v("Not initializing %s, initialization already completed", this);
                    initializationCompletable = Completable.complete();
                } else if (isInitializing) {
                    Timber.v("Deferring initialization of %s, initialization already in progress", this);
                    initializationCompletable = initialize(context)
                            .delaySubscription(10, TimeUnit.MILLISECONDS);
                } else {
                    isInitializing = true;
                    this.context = context;
                    this.managerDisposable = new CompositeDisposable();
                    initializationCompletable = doInitialize(context)
                            .doOnSubscribe(disposable -> Timber.d("Initializing %s", this))
                            .doOnComplete(() -> {
                                isInitialized = true;
                                Timber.i("Completed initialization of %s", this);
                            })
                            .doFinally(() -> isInitializing = false);
                }
            }
            return initializationCompletable;
        });
    }

    protected abstract Completable doInitialize(@NonNull Context context);

    @CallSuper
    public void dispose() {
        isInitialized = false;
        managerDisposable.dispose();
    }

    protected <Type> Single<Type> getInitializedField(Type field) {
        return Single.defer(() -> {
            if (field != null) {
                return Single.just(field);
            } else {
                return Single.error(new IllegalStateException(this.getClass().getSimpleName() + " has not been initialized yet"));
            }
        });
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    public boolean isInitializing() {
        return isInitializing;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

}
