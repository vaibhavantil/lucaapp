package de.culture4life.luca.meeting;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public class MeetingCreationResponse {

    @Expose
    @SerializedName("locationId")
    private UUID locationUuid;

    @Expose
    @SerializedName("scannerId")
    private UUID scannerUuid;

    @Expose
    @SerializedName("accessId")
    private UUID accessUuid;

    public UUID getLocationUuid() {
        return locationUuid;
    }

    public UUID getScannerUuid() {
        return scannerUuid;
    }

    public UUID getAccessUuid() {
        return accessUuid;
    }

}
