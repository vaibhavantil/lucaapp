package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.SerializedName;

public class HealthDepartment {

    @SerializedName("departmentId")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("publicHDEKP")
    private String publicHDEKP;

    @SerializedName("publicHDSKP")
    private String publicHDSKP;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPublicHDEKP() {
        return publicHDEKP;
    }

    public void setPublicHDEKP(String publicHDEKP) {
        this.publicHDEKP = publicHDEKP;
    }

    public String getPublicHDSKP() {
        return publicHDSKP;
    }

    public void setPublicHDSKP(String publicHDSKP) {
        this.publicHDSKP = publicHDSKP;
    }

    @Override
    public String toString() {
        return "HealthDepartment{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", publicHDEKP='" + publicHDEKP + '\'' +
                ", publicHDSKP='" + publicHDSKP + '\'' +
                '}';
    }

}
