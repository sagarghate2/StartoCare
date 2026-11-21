package com.startocare.bookingservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TravelPricingConfigRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal baseFarePerLeg,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal ratePerKm
) {
}
