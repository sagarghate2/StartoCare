package com.startocare.bookingservice.dto.request;

import com.startocare.bookingservice.enums.BookingStatus;
import jakarta.validation.constraints.NotNull;

public record BookingStatusUpdateRequest(@NotNull BookingStatus status) {
}
