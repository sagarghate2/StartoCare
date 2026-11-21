package com.startocare.bookingservice.exception;

public class EstimateUnavailableException extends RuntimeException {

    public EstimateUnavailableException(String message) {
        super(message);
    }

    public EstimateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
