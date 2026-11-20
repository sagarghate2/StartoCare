package com.startocare.bookingservice.dto.request;

import com.startocare.bookingservice.enums.TravelPlan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record EstimateRequest(
        @NotNull UUID companionId,
        @NotNull Integer durationMinutes,
        @NotNull TravelPlan travelPlan,
        @NotBlank String hospitalName,
        @NotBlank String hospitalAddress,
        @NotBlank String patientName,
        @NotNull LocalDate appointmentDate,
        @NotNull LocalTime appointmentTime,
        String patientHomeAddress
) {
}
