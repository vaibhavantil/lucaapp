package de.culture4life.luca.location;

public class LocationUnavailableException extends Exception {

    public LocationUnavailableException() {
    }

    public LocationUnavailableException(String message) {
        super(message);
    }

    public LocationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public LocationUnavailableException(Throwable cause) {
        super(cause);
    }

}
