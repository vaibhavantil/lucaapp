package de.culture4life.luca.ui.accesseddata;

import android.content.Context;

import androidx.annotation.Nullable;

public class AccessedDataListItem {

    protected String title;
    protected String description;
    protected String time;
    protected long timestamp;

    public AccessedDataListItem(Context context) {
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

}
