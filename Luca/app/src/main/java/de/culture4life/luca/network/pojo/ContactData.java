package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import de.culture4life.luca.registration.RegistrationData;

import androidx.annotation.NonNull;

/**
 * Example:
 *
 * <pre>
 * {
 *   "v": 3, // version
 *   "fn": "Donald", // firstName
 *   "ln": "Duck", // lastName
 *   "pn": "123 456 789", // phoneNumber (E.164/FQTN)
 *   "e": "donald.duck@entenhausen.de", // email
 *   "st": "Quackstraße", // street
 *   "hn": "42", // house number
 *   "pc": "12345", // postal code
 *   "c": "Entenhausen", // city
 * }
 * </pre>
 */
public class ContactData {

    @Expose
    @SerializedName("v")
    private int version = 3;

    @Expose
    @SerializedName("fn")
    private String firstName;

    @Expose
    @SerializedName("ln")
    private String lastName;

    @Expose
    @SerializedName("pn")
    private String phoneNumber;

    @Expose
    @SerializedName("e")
    private String email;

    @Expose
    @SerializedName("st")
    private String street;

    @Expose
    @SerializedName("hn")
    private String houseNumber;

    @Expose
    @SerializedName("c")
    private String city;

    @Expose
    @SerializedName("pc")
    private String postalCode;

    public ContactData() {
    }

    public ContactData(@NonNull RegistrationData registrationData) {
        setRegistrationData(registrationData);
    }

    public void setRegistrationData(@NonNull RegistrationData registrationData) {
        this.firstName = registrationData.getFirstName();
        this.lastName = registrationData.getLastName();
        this.phoneNumber = registrationData.getPhoneNumber();
        this.email = registrationData.getEmail();
        this.street = registrationData.getStreet();
        this.houseNumber = registrationData.getHouseNumber();
        this.city = registrationData.getCity();
        this.postalCode = registrationData.getPostalCode();
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
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

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getHouseNumber() {
        return houseNumber;
    }

    public void setHouseNumber(String houseNumber) {
        this.houseNumber = houseNumber;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    @Override
    public String toString() {
        return "ContactData{" +
                "version=" + version +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", email='" + email + '\'' +
                ", street='" + street + '\'' +
                ", houseNumber='" + houseNumber + '\'' +
                ", city='" + city + '\'' +
                ", postalCode='" + postalCode + '\'' +
                '}';
    }

}
