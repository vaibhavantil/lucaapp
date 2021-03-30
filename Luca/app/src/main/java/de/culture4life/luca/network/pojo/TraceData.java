package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TraceData {

    @Expose
    @SerializedName("traceId")
    private String traceId;

    @Expose
    @SerializedName("createdAt")
    private long checkInTimestamp;

    @Expose
    @SerializedName("checkout")
    private long checkOutTimestamp;

    @Expose
    @SerializedName("locationId")
    private String locationId;

    public TraceData() {
    }

    public boolean isCheckedOut() {
        return checkOutTimestamp > 0;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public long getCheckInTimestamp() {
        return checkInTimestamp;
    }

    public void setCheckInTimestamp(long checkInTimestamp) {
        this.checkInTimestamp = checkInTimestamp;
    }

    public long getCheckOutTimestamp() {
        return checkOutTimestamp;
    }

    public void setCheckOutTimestamp(long checkOutTimestamp) {
        this.checkOutTimestamp = checkOutTimestamp;
    }

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    @Override
    public String toString() {
        return "TraceData{" +
                "traceId='" + traceId + '\'' +
                ", checkInTimestamp=" + checkInTimestamp +
                ", checkOutTimestamp=" + checkOutTimestamp +
                ", locationId='" + locationId + '\'' +
                '}';
    }

}
