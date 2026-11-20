package com.startocare.bookingservice.dto.response;

import java.math.BigDecimal;

public record TravelPricingConfigResponse(
        boolean configured,
        BigDecimal baseFarePerLeg,
        BigDecimal ratePerKm,
        String currency
) {
}
