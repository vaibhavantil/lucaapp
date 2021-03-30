package de.culture4life.luca.util;

import com.google.android.gms.tasks.Task;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

public final class RxTasks {

    private RxTasks() {
    }

    public static <ResultType> Maybe<ResultType> toMaybe(final Task<ResultType> task) {
        return Maybe.create(emitter -> {
            task.addOnSuccessListener(result -> {
                if (!emitter.isDisposed()) {
                    if (result != null) {
                        emitter.onSuccess(result);
                    } else {
                        emitter.onComplete();
                    }
                }
            });
            task.addOnFailureListener(emitter::tryOnError);
            task.addOnCanceledListener(() -> emitter.tryOnError(new InterruptedException("The task has been canceled")));
        });
    }

    public static <ResultType> Single<ResultType> toSingle(final Task<ResultType> task) {
        return toMaybe(task).toSingle();
    }

    public static <ResultType> Completable toCompletable(final Task<ResultType> task) {
        return toMaybe(task).ignoreElement();
    }

}
