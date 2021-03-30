package de.culture4life.luca.preference;

import com.google.gson.GsonBuilder;

import android.content.Context;

import com.nexenio.rxpreferences.provider.BasePreferencesProvider;
import com.nexenio.rxpreferences.provider.InMemoryPreferencesProvider;
import com.nexenio.rxpreferences.provider.PreferencesProvider;
import com.nexenio.rxpreferences.provider.TrayPreferencesProvider;
import com.nexenio.rxpreferences.serializer.GsonSerializer;

import de.culture4life.luca.LucaApplication;
import de.culture4life.luca.Manager;
import de.culture4life.luca.crypto.TraceIdWrapper;
import de.culture4life.luca.history.HistoryItem;

import net.grandcentrix.tray.TrayPreferences;
import net.grandcentrix.tray.core.TrayStorage;

import androidx.annotation.NonNull;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

public class PreferencesManager extends Manager implements PreferencesProvider {

    private static final int VERSION = 1;

    private PreferencesProvider provider;

    @Override
    public Completable doInitialize(@NonNull Context context) {
        return Completable.fromAction(() -> {
            BasePreferencesProvider preferencesProvider;
            if (LucaApplication.isRunningUnitTests()) {
                preferencesProvider = new InMemoryPreferencesProvider();
            } else {
                TrayPreferences trayPreferences = new TrayPreferences(context, context.getPackageName(), VERSION, TrayStorage.Type.DEVICE);
                preferencesProvider = new TrayPreferencesProvider(trayPreferences);
            }
            preferencesProvider.setSerializer(new GsonSerializer(new GsonBuilder()
                    .setPrettyPrinting()
                    .excludeFieldsWithoutExposeAnnotation()
                    .registerTypeAdapter(TraceIdWrapper.class, new TraceIdWrapper.TypeAdapter())
                    .registerTypeAdapter(HistoryItem.class, new HistoryItem.TypeAdapter())
                    .create()));

            this.provider = preferencesProvider;
        }).andThen(persistDefaultValues());
    }

    private Completable persistDefaultValues() {
        return Completable.complete();
    }

    @Override
    public Observable<String> getKeys() {
        return getInitializedField(provider)
                .flatMapObservable(PreferencesProvider::getKeys);
    }

    @Override
    public Single<Boolean> containsKey(@NonNull String key) {
        return getInitializedField(provider)
                .flatMap(provider -> provider.containsKey(key));
    }

    @Override
    public <Type> Single<Type> restore(@NonNull String key, @NonNull Class<Type> typeClass) {
        return getInitializedField(provider)
                .flatMap(provider -> provider.restore(key, typeClass));
    }

    @Override
    public <Type> Single<Type> restoreOrDefault(@NonNull String key, @NonNull Type defaultValue) {
        return getInitializedField(provider)
                .flatMap(provider -> provider.restoreOrDefault(key, defaultValue));
    }

    @Override
    public <Type> Observable<Type> restoreOrDefaultAndGetChanges(@NonNull String key, @NonNull Type defaultValue) {
        return getInitializedField(provider)
                .flatMapObservable(provider -> provider.restoreOrDefaultAndGetChanges(key, defaultValue));
    }

    @Override
    public <Type> Maybe<Type> restoreIfAvailable(@NonNull String key, @NonNull Class<Type> typeClass) {
        return getInitializedField(provider)
                .flatMapMaybe(provider -> provider.restoreIfAvailable(key, typeClass));
    }

    @Override
    public <Type> Observable<Type> restoreIfAvailableAndGetChanges(@NonNull String key, @NonNull Class<Type> typeClass) {
        return getInitializedField(provider)
                .flatMapObservable(provider -> provider.restoreIfAvailableAndGetChanges(key, typeClass));
    }

    @Override
    public <Type> Completable persist(@NonNull String key, @NonNull Type value) {
        return getInitializedField(provider)
                .flatMapCompletable(provider -> provider.persist(key, value));
    }

    @Override
    public <Type> Completable persistIfNotYetAvailable(@NonNull String key, @NonNull Type value) {
        return getInitializedField(provider)
                .flatMapCompletable(provider -> provider.persistIfNotYetAvailable(key, value));
    }

    @Override
    public <Type> Observable<Type> getChanges(@NonNull String key, @NonNull Class<Type> typeClass) {
        return getInitializedField(provider)
                .flatMapObservable(provider -> provider.getChanges(key, typeClass));
    }

    @Override
    public Completable delete(@NonNull String key) {
        return getInitializedField(provider)
                .flatMapCompletable(provider -> provider.delete(key));
    }

    @Override
    public Completable deleteAll() {
        return getInitializedField(provider)
                .flatMapCompletable(PreferencesProvider::deleteAll);
    }

}
