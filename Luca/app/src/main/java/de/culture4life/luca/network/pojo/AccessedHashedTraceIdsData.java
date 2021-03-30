package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class AccessedHashedTraceIdsData {

    @SerializedName("healthDepartment")
    private HealthDepartment healthDepartment;

    @SerializedName("hashedTraceIds")
    private List<String> hashedTraceIds = new ArrayList<>();

    public HealthDepartment getHealthDepartment() {
        return healthDepartment;
    }

    public void setHealthDepartment(HealthDepartment healthDepartment) {
        this.healthDepartment = healthDepartment;
    }

    public List<String> getHashedTraceIds() {
        return hashedTraceIds;
    }

    public void setHashedTraceIds(List<String> hashedTraceIds) {
        this.hashedTraceIds = hashedTraceIds;
    }

    @Override
    public String toString() {
        return "AccessedHashedTraceIdsData{" +
                "healthDepartment=" + healthDepartment +
                ", hashedTraceIds=" + hashedTraceIds +
                '}';
    }

}
