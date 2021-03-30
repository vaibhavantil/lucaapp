package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.SerializedName;

public class CheckInRequestData {

    @SerializedName("traceId")
    private String traceId;

    @SerializedName("scannerId")
    private String scannerId;

    @SerializedName("timestamp")
    private long unixTimestamp;

    @SerializedName("data")
    private String reEncryptedQrCodeData;

    @SerializedName("iv")
    private String iv;

    @SerializedName("mac")
    private String mac;

    @SerializedName("publicKey")
    private String scannerEphemeralPublicKey;

    @SerializedName("deviceType")
    private int deviceType;

    public CheckInRequestData() {
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getScannerId() {
        return scannerId;
    }

    public void setScannerId(String scannerId) {
        this.scannerId = scannerId;
    }

    public long getUnixTimestamp() {
        return unixTimestamp;
    }

    public void setUnixTimestamp(long unixTimestamp) {
        this.unixTimestamp = unixTimestamp;
    }

    public String getReEncryptedQrCodeData() {
        return reEncryptedQrCodeData;
    }

    public void setReEncryptedQrCodeData(String reEncryptedQrCodeData) {
        this.reEncryptedQrCodeData = reEncryptedQrCodeData;
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

    public String getScannerEphemeralPublicKey() {
        return scannerEphemeralPublicKey;
    }

    public void setScannerEphemeralPublicKey(String scannerEphemeralPublicKey) {
        this.scannerEphemeralPublicKey = scannerEphemeralPublicKey;
    }

    public int getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(int deviceType) {
        this.deviceType = deviceType;
    }

    @Override
    public String toString() {
        return "CheckInRequestData{" +
                "traceId='" + traceId + '\'' +
                ", scannerId='" + scannerId + '\'' +
                ", unixTimestamp=" + unixTimestamp +
                ", reEncryptedQrCodeData='" + reEncryptedQrCodeData + '\'' +
                ", iv='" + iv + '\'' +
                ", mac='" + mac + '\'' +
                ", scannerEphemeralPublicKey='" + scannerEphemeralPublicKey + '\'' +
                ", deviceType=" + deviceType +
                '}';
    }

}
