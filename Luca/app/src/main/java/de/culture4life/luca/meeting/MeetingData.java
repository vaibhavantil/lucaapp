package de.culture4life.luca.meeting;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MeetingData {

    @Expose
    @SerializedName("locationId")
    private UUID locationId;

    @Expose
    @SerializedName("accessId")
    private UUID accessId;

    @Expose
    @SerializedName("scannerId")
    private UUID scannerId;

    @Expose
    @SerializedName("creationTimestamp")
    private long creationTimestamp;

    @Expose
    @SerializedName("guestData")
    private List<MeetingGuestData> guestData;

    public MeetingData() {
        guestData = new ArrayList<>();
    }

    public MeetingData(MeetingCreationResponse meetingCreationResponse) {
        this.locationId = meetingCreationResponse.getLocationUuid();
        this.accessId = meetingCreationResponse.getAccessUuid();
        this.scannerId = meetingCreationResponse.getScannerUuid();
        this.creationTimestamp = System.currentTimeMillis();
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public UUID getAccessId() {
        return accessId;
    }

    public void setAccessId(UUID accessId) {
        this.accessId = accessId;
    }

    public UUID getScannerId() {
        return scannerId;
    }

    public void setScannerId(UUID scannerId) {
        this.scannerId = scannerId;
    }

    public long getCreationTimestamp() {
        return creationTimestamp;
    }

    public void setCreationTimestamp(long creationTimestamp) {
        this.creationTimestamp = creationTimestamp;
    }

    public List<MeetingGuestData> getGuestData() {
        return guestData;
    }

    public void setGuestData(List<MeetingGuestData> guestData) {
        this.guestData = guestData;
    }

    @Override
    public String toString() {
        return "MeetingData{" +
                "locationId=" + locationId +
                ", accessId=" + accessId +
                ", scannerId=" + scannerId +
                ", creationTimestamp=" + creationTimestamp +
                ", guestData=" + guestData +
                '}';
    }

}
