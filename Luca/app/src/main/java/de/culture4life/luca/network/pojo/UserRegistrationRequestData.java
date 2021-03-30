package de.culture4life.luca.network.pojo;

import com.google.gson.annotations.SerializedName;

/**
 * Example:
 *
 * <pre>
 * {
 *   "data": "u1GUhaHK3lCY+AJuc8m5ZN0ncE+Yypc7o4VeYavV8pbwbFM+5mpOWTzhisCbsLcUiApARPrHAaEyT75lVA/hdRUEJ4Z6p/uinEkb9N6KJ4fgQYK2PoYYhaSRdapTH3ETYXkWVmxdFBBDYKTZV1eaCBYCgZqF3Ydx1Pxxt9TMaMCFimUVW2CyiZXbofWhnVfXIBnvEFHnfgQbJjxWCZ2IP4JoCT28Rmvi/AHC4PGs2hysLv7WbXE9IUEnLw7PvplK",
 *   "iv": "ZM+ygFEw7YJgqizwNE/k2A==",
 *   "publicKey": "BIMFVAOglk1B4PIlpaVspeWeFwO5eUusqxFAUUDFNJYGpbp9iu0jRHQAipDTVgFSudcm9tF5kh4+wILrAm3vHWg=",
 *   "signature": "MEUCIQCEbDo2u2IZ2mEQV5xLpZH7m9Xy6yum61eXsqHZo+k3wQIgAnCteTv20ERfJm7vP+cfoUZwbmTKK/i9SmmNW42eB9k=",
 *   "keyId": 0
 * }
 * </pre>
 */
public class UserRegistrationRequestData {

    @SerializedName("data")
    private String encryptedContactData;

    @SerializedName("iv")
    private String iv;

    @SerializedName("publicKey")
    private String guestKeyPairPublicKey;

    @SerializedName("mac")
    private String mac;

    @SerializedName("signature")
    private String signature;

    public String getEncryptedContactData() {
        return encryptedContactData;
    }

    public void setEncryptedContactData(String encryptedPersonalData) {
        this.encryptedContactData = encryptedPersonalData;
    }

    public String getIv() {
        return iv;
    }

    public void setIv(String iv) {
        this.iv = iv;
    }

    public String getGuestKeyPairPublicKey() {
        return guestKeyPairPublicKey;
    }

    public void setGuestKeyPairPublicKey(String guestKeyPairPublicKey) {
        this.guestKeyPairPublicKey = guestKeyPairPublicKey;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @Override
    public String toString() {
        return "UserRegistrationRequestData{" +
                "encryptedContactData='" + encryptedContactData + '\'' +
                ", iv='" + iv + '\'' +
                ", guestKeyPairPublicKey='" + guestKeyPairPublicKey + '\'' +
                ", mac='" + mac + '\'' +
                ", signature='" + signature + '\'' +
                '}';
    }

}
