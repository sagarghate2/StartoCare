package com.startocare.bookingservice.dto.response;

import com.startocare.bookingservice.enums.BookingStatus;
import com.startocare.bookingservice.enums.TravelPlan;
import com.startocare.bookingservice.enums.TravelQuoteStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        String bookingReference,
        UUID familyUserId,
        UUID companionId,
        Integer durationMinutes,
        TravelPlan travelPlan,
        String patientName,
        String hospitalName,
        String hospitalAddress,
        String patientHomeAddress,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        BigDecimal serviceAmount,
        BigDecimal travelAmount,
        BigDecimal totalAmount,
        TravelQuoteStatus travelQuoteStatus,
        BookingStatus bookingStatus,
        List<TravelLegQuoteResponse> travelLegs,
        Instant createdAt,
        Instant updatedAt
) {
}
