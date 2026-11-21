package com.startocare.bookingservice.enums;

import java.util.Locale;

public enum BookingStatus {
    AWAITING_PAYMENT,
    PENDING,
    CONFIRMED,
    DECLINED,
    COMPANION_ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public static BookingStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Booking status is required");
        }
        return BookingStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
