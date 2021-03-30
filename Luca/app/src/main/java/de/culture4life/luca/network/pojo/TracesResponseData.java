package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.SerializedName;

public class TracesResponseData {

    @SerializedName("traceId")
    private String traceId;

    @SerializedName("checkin")
    private Long checkInTimestamp;

    @SerializedName("checkout")
    private Long checkOutTimestamp;

    @SerializedName("data")
    private AdditionalData additionalData;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Long getCheckInTimestamp() {
        return checkInTimestamp;
    }

    public void setCheckInTimestamp(Long checkInTimestamp) {
        this.checkInTimestamp = checkInTimestamp;
    }

    public Long getCheckOutTimestamp() {
        return checkOutTimestamp;
    }

    public long getCheckOutTimestampOrZero() {
        return checkOutTimestamp != null ? checkOutTimestamp : 0;
    }

    public void setCheckOutTimestamp(Long checkOutTimestamp) {
        this.checkOutTimestamp = checkOutTimestamp;
    }

    public AdditionalData getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(AdditionalData additionalData) {
        this.additionalData = additionalData;
    }

    @Override
    public String toString() {
        return "GuestData{" +
                "traceId='" + traceId + '\'' +
                ", checkInTimestamp=" + checkInTimestamp +
                ", checkOutTimestamp=" + checkOutTimestamp +
                ", additionalData=" + additionalData +
                '}';
    }

    public static class AdditionalData {

        @SerializedName("data")
        private String data;

        @SerializedName("iv")
        private String iv;

        @SerializedName("mac")
        private String mac;

        @SerializedName("publicKey")
        private String publicKey;

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
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

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        @Override
        public String toString() {
            return "AdditionalData{" +
                    "data='" + data + '\'' +
                    ", iv='" + iv + '\'' +
                    ", mac='" + mac + '\'' +
                    ", publicKey='" + publicKey + '\'' +
                    '}';
        }

    }

}
