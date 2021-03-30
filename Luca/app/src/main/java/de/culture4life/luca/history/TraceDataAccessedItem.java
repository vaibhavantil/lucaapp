package de.culture4life.luca.history;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TraceDataAccessedItem extends HistoryItem {

    @Expose
    @SerializedName("healthDepartmentName")
    private String healthDepartmentName;

    @Expose
    @SerializedName("healthDepartmentId")
    private String healthDepartmentId;

    @Expose
    @SerializedName("tracingId")
    private String traceId;

    @Expose
    @SerializedName("locationName")
    private String locationName;

    @Expose
    @SerializedName("checkInTimestamp")
    private long checkInTimestamp;

    @Expose
    @SerializedName("checkOutTimestamp")
    private long checkOutTimestamp;

    public TraceDataAccessedItem() {
        super(TYPE_TRACE_DATA_ACCESSED);
    }

    public String getHealthDepartmentName() {
        return healthDepartmentName;
    }

    public void setHealthDepartmentName(String healthDepartmentName) {
        this.healthDepartmentName = healthDepartmentName;
    }

    public String getHealthDepartmentId() {
        return healthDepartmentId;
    }

    public void setHealthDepartmentId(String healthDepartmentId) {
        this.healthDepartmentId = healthDepartmentId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
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

    @Override
    public String toString() {
        return "TraceDataAccessedItem{" +
                "healthDepartmentName='" + healthDepartmentName + '\'' +
                ", healthDepartmentId='" + healthDepartmentId + '\'' +
                ", traceId='" + traceId + '\'' +
                ", locationName='" + locationName + '\'' +
                ", checkInTimestamp=" + checkInTimestamp +
                ", checkOutTimestamp=" + checkOutTimestamp +
                "} " + super.toString();
    }

}
