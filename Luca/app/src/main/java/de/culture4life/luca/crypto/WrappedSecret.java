package de.culture4life.luca.crypto;

import com.google.gson.annotations.Expose;

import android.util.Pair;

import de.culture4life.luca.util.SerializationUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WrappedSecret {

    @Expose
    private String encryptedSecret;

    @Nullable
    @Expose
    private String iv;

    public WrappedSecret() {
    }

    public WrappedSecret(@NonNull byte[] encryptedSecret) {
        this.encryptedSecret = SerializationUtil.serializeToBase64(encryptedSecret).blockingGet();
    }

    public WrappedSecret(@NonNull Pair<byte[], byte[]> encryptedSecretAndIv) {
        this.encryptedSecret = SerializationUtil.serializeToBase64(encryptedSecretAndIv.first).blockingGet();
        if (encryptedSecretAndIv.second != null) {
            this.iv = SerializationUtil.serializeToBase64(encryptedSecretAndIv.second).blockingGet();
        }
    }

    public String getEncryptedSecret() {
        return encryptedSecret;
    }

    public byte[] getDeserializedEncryptedSecret() {
        return SerializationUtil.deserializeFromBase64(encryptedSecret).blockingGet();
    }

    public void setEncryptedSecret(String encryptedSecret) {
        this.encryptedSecret = encryptedSecret;
    }

    @Nullable
    public String getIv() {
        return iv;
    }

    @Nullable
    public byte[] getDeserializedIv() {
        if (iv == null) {
            return null;
        }
        return SerializationUtil.deserializeFromBase64(iv).blockingGet();
    }

    public void setIv(@Nullable String iv) {
        this.iv = iv;
    }

}
