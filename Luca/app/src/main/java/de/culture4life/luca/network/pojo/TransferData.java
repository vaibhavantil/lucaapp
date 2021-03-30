package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Example:
 *
 * <pre>
 * {
 *   "v": 3, // version
 *   "uid": "02fb635c-f6a5-48eb-8379-a83d611618f2", // userId
 *   "uts": [ "dZrDSp83PCcVL5ZvsJypwA==" ], // userTraceSecrets (759ac34a9f373c27152f966fb09ca9c0)
 *   "uds": "OUR2Tnpohdf6ZQukgqPZ", // userDataSecret (3944764e7a6885d7fa650ba482a3d9)
 * }
 * </pre>
 */
public class TransferData {

    @Expose
    @SerializedName("v")
    private int version = 3;

    @Expose
    @SerializedName("uid")
    private String userId;

    @Expose
    @SerializedName("uts")
    private List<TraceSecretWrapper> traceSecretWrappers;

    @Expose
    @SerializedName("uds")
    private String dataSecret;

    public TransferData() {
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<TraceSecretWrapper> getTraceSecretWrappers() {
        return traceSecretWrappers;
    }

    public void setTraceSecretWrappers(List<TraceSecretWrapper> traceSecretWrappers) {
        this.traceSecretWrappers = traceSecretWrappers;
    }

    public String getDataSecret() {
        return dataSecret;
    }

    public void setDataSecret(String userDataSecret) {
        this.dataSecret = userDataSecret;
    }

    @Override
    public String toString() {
        return "TransferData{" +
                "version=" + version +
                ", userId='" + userId + '\'' +
                ", traceSecretWrappers=" + traceSecretWrappers +
                ", dataSecret='" + dataSecret + '\'' +
                '}';
    }

    public static class TraceSecretWrapper {

        @Expose
        @SerializedName("ts")
        private long timestamp;

        @Expose
        @SerializedName("s")
        private String secret;

        public TraceSecretWrapper() {
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        @Override
        public String toString() {
            return "TraceSecret{" +
                    "timestamp=" + timestamp +
                    ", secret='" + secret + '\'' +
                    '}';
        }

    }

}
