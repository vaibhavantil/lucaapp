package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.SerializedName;

public class DataTransferRequestData {

    @SerializedName("data")
    private String encryptedContactData;

    @SerializedName("iv")
    private String iv;

    @SerializedName("publicKey")
    private String guestKeyPairPublicKey;

    @SerializedName("mac")
    private String mac;

    @SerializedName("keyId")
    private int dailyKeyPairPublicKeyId;

    public String getEncryptedContactData() {
        return encryptedContactData;
    }

    public void setEncryptedContactData(String encryptedPersonalData) {
        this.encryptedContactData = encryptedPersonalData;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getGuestKeyPairPublicKey() {
        return guestKeyPairPublicKey;
    }

    public void setGuestKeyPairPublicKey(String guestKeyPairPublicKey) {
        this.guestKeyPairPublicKey = guestKeyPairPublicKey;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public int getDailyKeyPairPublicKeyId() {
        return dailyKeyPairPublicKeyId;
    }

    public void setDailyPublicKeyId(int rotatingBackendKeyId) {
        this.dailyKeyPairPublicKeyId = rotatingBackendKeyId;
    }

    @Override
    public String toString() {
        return "DataTransferRequestData{" +
                "encryptedContactData='" + encryptedContactData + '\'' +
                ", iv='" + iv + '\'' +
                ", guestKeyPairPublicKey='" + guestKeyPairPublicKey + '\'' +
                ", mac='" + mac + '\'' +
                ", dailyKeyPairPublicKeyId=" + dailyKeyPairPublicKeyId +
                '}';
    }

}
