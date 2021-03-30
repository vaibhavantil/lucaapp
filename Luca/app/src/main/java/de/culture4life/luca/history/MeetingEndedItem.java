package de.culture4life.luca.history;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class MeetingEndedItem extends HistoryItem {

    @SerializedName("guests")
    @Expose
    private List<String> guests = new ArrayList<>();

    public MeetingEndedItem() {
        super(HistoryItem.TYPE_MEETING_ENDED);
    }

    public List<String> getGuests() {
        return guests;
    }

    public void setGuests(List<String> guests) {
        this.guests = guests;
    }

    @Override
    public String toString() {
        return "MeetingEndedItem{" +
                "guests=" + guests +
                "} " + super.toString();
    }

}
