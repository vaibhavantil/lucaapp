package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.SerializedName;

public class LocationResponseData {

    @SerializedName("locationId")
    private String locationId;

    @SerializedName("locationName")
    private String areaName;

    @SerializedName("groupName")
    private String groupName;

    @SerializedName("lat")
    private double latitude;

    @SerializedName("lng")
    private double longitude;

    @SerializedName("radius")
    private long radius;

    public String getLocationId() {
        return locationId;
    }

    public void setLocationId(String locationId) {
        this.locationId = locationId;
    }

    public String getAreaName() {
        return areaName;
    }

    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
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

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public long getRadius() {
        return radius;
    }

    public void setRadius(long radius) {
        this.radius = radius;
    }

    @Override
    public String toString() {
        return "LocationResponseData{" +
                "locationId='" + locationId + '\'' +
                ", areaName='" + areaName + '\'' +
                ", groupName='" + groupName + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", radius=" + radius +
                '}';
    }

}
