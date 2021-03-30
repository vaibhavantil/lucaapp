package de.culture4life.luca.meeting;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class ArchivedMeetingData {

    @Expose
    @SerializedName("meetings")
    private List<MeetingData> meetings;

    public ArchivedMeetingData() {
        meetings = new ArrayList<>();
    }

    public ArchivedMeetingData(List<MeetingData> archivedMeetings) {
        this.meetings = archivedMeetings;
    }

    public List<MeetingData> getMeetings() {
        return meetings;
    }

    public void setMeetings(List<MeetingData> meetings) {
        this.meetings = meetings;
    }

    @Override
    public String toString() {
        return "ArchivedMeetingData{" +
                "meetings=" + meetings +
                '}';
    }

}
