package com.startocare.bookingservice.dto.response;

import java.util.UUID;

public record CompanionLocationResponse(
        UUID id,
        String serviceLocationAddress,
        double latitude,
        double longitude
) {
}
