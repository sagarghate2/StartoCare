package com.startocare.bookingservice.enums;

import java.util.Locale;

public enum TravelQuoteStatus {
    PENDING,
    QUOTED,
    CONFIRMED;

    public static TravelQuoteStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Travel quote status is required");
        }
        return TravelQuoteStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
