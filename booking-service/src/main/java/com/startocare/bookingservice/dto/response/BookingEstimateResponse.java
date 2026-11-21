package com.startocare.bookingservice.dto.response;

import com.startocare.bookingservice.enums.TravelPlan;
import com.startocare.bookingservice.enums.TravelQuoteStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BookingEstimateResponse(
        UUID companionId,
        Integer durationMinutes,
        TravelPlan travelPlan,
        BigDecimal serviceAmount,
        BigDecimal travelAmount,
        BigDecimal totalAmount,
        TravelQuoteStatus travelQuoteStatus,
        List<TravelLegQuoteResponse> travelLegs
) {
}
