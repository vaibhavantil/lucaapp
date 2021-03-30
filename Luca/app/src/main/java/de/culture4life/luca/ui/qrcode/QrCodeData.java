package de.culture4life.luca.ui.qrcode;

import de.culture4life.luca.util.SerializationUtil;

import java.lang.annotation.Retention;

import androidx.annotation.IntDef;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public class QrCodeData {

    private static final byte VERSION_CURRENT = 3;

    @IntDef({TYPE_IOS, TYPE_ANDROID, TYPE_STATIC})
    @Retention(SOURCE)
    public @interface Type {

    }

    private static final int TYPE_IOS = 0;
    private static final int TYPE_ANDROID = 1;
    private static final int TYPE_STATIC = 2;

    private byte version = VERSION_CURRENT;

    private byte deviceType = TYPE_ANDROID;

    private byte keyId;

    private byte[] timestamp;

    private byte[] traceId;

    private byte[] encryptedData;

    private byte[] userEphemeralPublicKey;

    private byte[] verificationTag;

    public byte getVersion() {
        return version;
    }

    public void setVersion(byte version) {
        this.version = version;
    }

    public byte getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(byte deviceType) {
        this.deviceType = deviceType;
    }

    public void setDeviceType(@Type int deviceType) {
        setDeviceType((byte) deviceType);
    }

    public byte getKeyId() {
        return keyId;
    }

    public void setKeyId(byte keyId) {
        this.keyId = keyId;
    }

    public void setKeyId(int keyId) {
        setKeyId((byte) keyId);
    }

    public byte[] getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(byte[] timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getTraceId() {
        return traceId;
    }

    public void setTraceId(byte[] traceId) {
        this.traceId = traceId;
    }

    public byte[] getEncryptedData() {
        return encryptedData;
    }

    public void setEncryptedData(byte[] encryptedData) {
        this.encryptedData = encryptedData;
    }

    public byte[] getUserEphemeralPublicKey() {
        return userEphemeralPublicKey;
    }

    public void setUserEphemeralPublicKey(byte[] userEphemeralPublicKey) {
        this.userEphemeralPublicKey = userEphemeralPublicKey;
    }

    public byte[] getVerificationTag() {
        return verificationTag;
    }

    public void setVerificationTag(byte[] verificationTag) {
        this.verificationTag = verificationTag;
    }

    @Override
    public String toString() {
        return "QrCodeData{" +
                "version=" + version +
                ", deviceType=" + deviceType +
                ", keyId=" + keyId +
                ", timestamp=" + SerializationUtil.serializeToBase64(timestamp).blockingGet() +
                ", traceId=" + SerializationUtil.serializeToBase64(traceId).blockingGet() +
                ", encryptedData=" + SerializationUtil.serializeToBase64(encryptedData).blockingGet() +
                ", userEphemeralPublicKey=" + SerializationUtil.serializeToBase64(userEphemeralPublicKey).blockingGet() +
                ", verificationTag=" + SerializationUtil.serializeToBase64(verificationTag).blockingGet() +
                '}';
    }

}
