package de.culture4life.luca.checkin;

import java.lang.annotation.Retention;

import androidx.annotation.IntDef;

import static java.lang.annotation.RetentionPolicy.SOURCE;

public class CheckOutException extends Exception {

    @IntDef({UNKNOWN_ERROR, MISSING_PERMISSION_ERROR, MINIMUM_DURATION_ERROR, MINIMUM_DISTANCE_ERROR, LOCATION_UNAVAILABLE_ERROR})
    @Retention(SOURCE)
    public @interface CheckOutErrorCode {

    }

    public final static int UNKNOWN_ERROR = 0;
    public final static int MISSING_PERMISSION_ERROR = 1;
    public final static int MINIMUM_DURATION_ERROR = 2;
    public final static int MINIMUM_DISTANCE_ERROR = 3;
    public final static int LOCATION_UNAVAILABLE_ERROR = 4;

    private final int errorCode;

    public CheckOutException(@CheckOutErrorCode int errorCode) {
        this.errorCode = errorCode;
    }

    public CheckOutException(String message, @CheckOutErrorCode int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public CheckOutException(String message, Throwable cause, @CheckOutErrorCode int errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public CheckOutException(Throwable cause, @CheckOutErrorCode int errorCode) {
        super(cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }

}
