package com.startocare.bookingservice.exception;

public class ForbiddenBookingAccessException extends RuntimeException {
    public ForbiddenBookingAccessException(String message) {
        super(message);
    }
}
