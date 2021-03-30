package de.culture4life.luca.network;

public class NetworkUnavailableException extends Exception {

    public NetworkUnavailableException() {
    }

    public NetworkUnavailableException(String message) {
        super(message);
    }

    public NetworkUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public NetworkUnavailableException(Throwable cause) {
        super(cause);
    }

}
