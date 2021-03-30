package de.culture4life.luca.util;

import com.google.gson.Gson;

import android.util.Base64;

import com.nexenio.rxkeystore.util.RxBase64;

import androidx.annotation.NonNull;
import io.reactivex.rxjava3.core.Single;

public final class SerializationUtil {

    private static final Gson GSON = new Gson();

    private SerializationUtil() {
    }

    public static Single<String> serializeToJson(@NonNull Object object) {
        return Single.fromCallable(() -> GSON.toJson(object));
    }

    public static <Type> Single<Type> deserializeFromJson(@NonNull String json, Class<Type> typeClass) {
        return Single.fromCallable(() -> GSON.fromJson(json, typeClass));
    }

    public static Single<String> serializeToBase64(@NonNull byte[] bytes) {
        return RxBase64.encode(bytes, Base64.NO_WRAP);
    }

    public static Single<byte[]> deserializeFromBase64(@NonNull String base64) {
        return RxBase64.decode(base64);
    }

    public static Single<String> serializeToZ85(@NonNull byte[] bytes) {
        return Single.fromCallable(() -> Z85.encode(bytes));
    }

    public static Single<byte[]> deserializeFromZ85(@NonNull String z85) {
        return Single.fromCallable(() -> Z85.decode(z85));
    }

}
