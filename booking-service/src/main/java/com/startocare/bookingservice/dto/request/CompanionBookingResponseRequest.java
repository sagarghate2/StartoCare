package com.startocare.bookingservice.dto.request;

import com.startocare.bookingservice.enums.CompanionBookingDecision;
import jakarta.validation.constraints.NotNull;

public record CompanionBookingResponseRequest(@NotNull CompanionBookingDecision decision) {
}
