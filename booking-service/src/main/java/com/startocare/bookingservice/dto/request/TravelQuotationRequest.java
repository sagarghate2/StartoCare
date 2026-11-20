package com.startocare.bookingservice.dto.request;

import com.startocare.bookingservice.enums.TravelQuoteStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TravelQuotationRequest(
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal travelAmount,
        @NotNull TravelQuoteStatus travelQuoteStatus,
        String note
) {
}
