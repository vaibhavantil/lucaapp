package de.culture4life.luca.checkin;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.UUID;

import androidx.annotation.Nullable;

public class CheckInData {

    @SerializedName("traceId")
    @Expose
    private String traceId;

    @SerializedName("locationId")
    @Expose
    private UUID locationId;

    @SerializedName("locationName")
    @Expose
    private String locationAreaName;

    @SerializedName("locationGroupName")
    @Expose
    private String locationGroupName;

    @SerializedName("timestamp")
    @Expose
    private long timestamp;

    @SerializedName("latitude")
    @Expose
    private double latitude;

    @SerializedName("longitude")
    @Expose
    private double longitude;

    @SerializedName("radius")
    @Expose
    private long radius;

    @SerializedName("minimumDuration")
    @Expose
    private long minimumDuration;

    @Nullable
    public String getLocationDisplayName() {
        if (locationGroupName != null && locationAreaName != null) {
            return locationGroupName + " - " + locationAreaName;
        } else if (locationGroupName != null) {
            return locationGroupName;
        } else if (locationAreaName != null) {
            return locationAreaName;
        } else {
            return null;
        }
    }

    public boolean hasLocation() {
        return latitude != 0 && longitude != 0;
    }

    public boolean hasLocationRestriction() {
        return hasLocation() && radius > 0;
    }

    public boolean hasDurationRestriction() {
        return minimumDuration > 0;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public String getLocationAreaName() {
        return locationAreaName;
    }

    public void setLocationAreaName(String locationName) {
        this.locationAreaName = locationName;
    }

    public String getLocationGroupName() {
        return locationGroupName;
    }

    public void setLocationGroupName(String locationGroupName) {
        this.locationGroupName = locationGroupName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double lng) {
        this.longitude = lng;
    }

    public long getRadius() {
        return radius;
    }

    public void setRadius(long radius) {
        this.radius = radius;
    }

    public long getMinimumDuration() {
        return minimumDuration;
    }

    public void setMinimumDuration(long minimumDuration) {
        this.minimumDuration = minimumDuration;
    }

    @Override
    public String toString() {
        return "CheckInData{" +
                "traceId='" + traceId + '\'' +
                ", locationId=" + locationId +
                ", locationAreaName='" + locationAreaName + '\'' +
                ", locationGroupName='" + locationGroupName + '\'' +
                ", timestamp=" + timestamp +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", radius=" + radius +
                ", minimumDuration=" + minimumDuration +
                '}';
    }

}
