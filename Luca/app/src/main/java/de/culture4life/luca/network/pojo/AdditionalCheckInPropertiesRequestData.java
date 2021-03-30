package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.SerializedName;

public class AdditionalCheckInPropertiesRequestData {

    @SerializedName("traceId")
    private String traceId;

    @SerializedName("data")
    private String encryptedProperties;

    @SerializedName("iv")
    private String iv;

    @SerializedName("mac")
    private String mac;

    @SerializedName("publicKey")
    private String scannerPublicKey;

    public AdditionalCheckInPropertiesRequestData() {
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getEncryptedProperties() {
        return encryptedProperties;
    }

    public void setEncryptedProperties(String encryptedProperties) {
        this.encryptedProperties = encryptedProperties;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getScannerPublicKey() {
        return scannerPublicKey;
    }

    public void setScannerPublicKey(String scannerPublicKey) {
        this.scannerPublicKey = scannerPublicKey;
    }

    @Override
    public String toString() {
        return "AdditionalCheckInPropertiesRequestData{" +
                "traceId='" + traceId + '\'' +
                ", encryptedProperties='" + encryptedProperties + '\'' +
                ", iv='" + iv + '\'' +
                ", mac='" + mac + '\'' +
                ", scannerPublicKey='" + scannerPublicKey + '\'' +
                '}';
    }

}
