package com.startocare.bookingservice.enums;

import java.util.Locale;

public enum TravelPlan {
    PLAN_1,
    PLAN_2,
    PLAN_3;

    public static TravelPlan fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Travel plan is required");
        }

        String normalized = value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        if (normalized.equals("PLAN1")) {
            normalized = "PLAN_1";
        } else if (normalized.equals("PLAN2")) {
            normalized = "PLAN_2";
        } else if (normalized.equals("PLAN3")) {
            normalized = "PLAN_3";
        }

        return TravelPlan.valueOf(normalized);
    }
}
