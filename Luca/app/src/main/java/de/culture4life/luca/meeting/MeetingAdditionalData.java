package de.culture4life.luca.meeting;

import com.google.gson.annotations.SerializedName;

import de.culture4life.luca.registration.RegistrationData;

import androidx.annotation.NonNull;

public class MeetingAdditionalData {

    @SerializedName("fn")
    private String firstName;

    @SerializedName("ln")
    private String lastName;

    public MeetingAdditionalData() {
    }

    public MeetingAdditionalData(@NonNull RegistrationData registrationData) {
        this.firstName = registrationData.getFirstName();
        this.lastName = registrationData.getLastName();
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Override
    public String toString() {
        return "MeetingAdditionalData{" +
                "firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                '}';
    }

}
