package com.startocare.bookingservice.dto.response;

import java.math.BigDecimal;

public record TravelLegQuoteResponse(
        String leg,
        String from,
        String to,
        long distanceMeters,
        BigDecimal distanceKm,
        BigDecimal baseFare,
        BigDecimal distanceCharge,
        BigDecimal totalFare
) {
}
