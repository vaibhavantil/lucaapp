package de.culture4life.luca.ui.history;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

public class HistoryListItem {

    protected String title;
    protected String description;
    protected String additionalDetails;
    protected String time;
    protected long timestamp;
    @DrawableRes
    protected int iconResourceId;

    public HistoryListItem(Context context) {
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

    @Nullable
    public String getAdditionalDetails() {
        return additionalDetails;
    }

    public void setAdditionalDetails(@Nullable String additionalDetails) {
        this.additionalDetails = additionalDetails;
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

    @DrawableRes
    public int getIconResourceId() {
        return iconResourceId;
    }

    public void setIconResourceId(@DrawableRes int iconResourceId) {
        this.iconResourceId = iconResourceId;
    }

}
