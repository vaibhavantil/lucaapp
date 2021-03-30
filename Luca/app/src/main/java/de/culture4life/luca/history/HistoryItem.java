package de.culture4life.luca.history;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.lang.annotation.Retention;

import androidx.annotation.IntDef;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public class HistoryItem {

    @IntDef({TYPE_CHECK_IN, TYPE_CHECK_OUT, TYPE_CONTACT_DATA_UPDATE, TYPE_CONTACT_DATA_REQUEST,
            TYPE_MEETING_STARTED, TYPE_MEETING_ENDED, TYPE_DATA_DELETED, TYPE_TRACE_DATA_ACCESSED})
    @Retention(SOURCE)
    public @interface Type {

    }

    public final static int TYPE_CHECK_IN = 1;
    public final static int TYPE_CHECK_OUT = 2;
    public final static int TYPE_CONTACT_DATA_UPDATE = 3;
    public final static int TYPE_CONTACT_DATA_REQUEST = 4;
    public final static int TYPE_MEETING_ENDED = 5;
    public final static int TYPE_DATA_DELETED = 6;
    public final static int TYPE_MEETING_STARTED = 7;
    public final static int TYPE_TRACE_DATA_ACCESSED = 8;

    @SerializedName("type")
    @Expose
    private int type;

    @SerializedName("relatedId")
    @Expose
    private String relatedId;

    @SerializedName("timestamp")
    @Expose
    private long timestamp;

    @SerializedName("displayName")
    @Expose
    private String displayName;

    /**
     * Empty constructor for GSON.
     */
    public HistoryItem() {
    }

    public HistoryItem(@Type int type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    @Type
    public int getType() {
        return type;
    }

    public void setType(@Type int type) {
        this.type = type;
    }

    public String getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(String relatedId) {
        this.relatedId = relatedId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return "HistoryItem{" +
                "type=" + type +
                ", relatedId='" + relatedId + '\'' +
                ", timestamp=" + timestamp +
                ", displayName='" + displayName + '\'' +
                '}';
    }

    public static class TypeAdapter implements JsonSerializer<HistoryItem>, JsonDeserializer<HistoryItem> {

        private final Gson gson = new Gson();

        @Override
        public JsonElement serialize(HistoryItem item, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context) {
            return gson.toJsonTree(item, getItemClass(item.getType()));
        }

        @Override
        public HistoryItem deserialize(JsonElement jsonElement, java.lang.reflect.Type type, JsonDeserializationContext context) throws JsonParseException {
            int itemType = jsonElement.getAsJsonObject().get("type").getAsInt();
            return gson.fromJson(jsonElement, getItemClass(itemType));
        }

        private static Class<? extends HistoryItem> getItemClass(@Type int type) {
            switch (type) {
                case TYPE_MEETING_ENDED:
                    return MeetingEndedItem.class;
                case TYPE_TRACE_DATA_ACCESSED:
                    return TraceDataAccessedItem.class;
                default:
                    return HistoryItem.class;
            }
        }

    }

}
