package de.culture4life.luca.checkin;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class ArchivedCheckInData {

    @Expose
    @SerializedName("check-ins")
    private List<CheckInData> checkIns;

    public ArchivedCheckInData() {
        checkIns = new ArrayList<>();
    }

    public ArchivedCheckInData(List<CheckInData> archivedCheckIns) {
        this.checkIns = archivedCheckIns;
    }

    public List<CheckInData> getCheckIns() {
        return checkIns;
    }

    public void setCheckIns(List<CheckInData> checkIns) {
        this.checkIns = checkIns;
    }

    @Override
    public String toString() {
        return "ArchivedCheckInData{" +
                "checkIns=" + checkIns +
                '}';
    }

}
