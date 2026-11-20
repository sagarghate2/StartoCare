package com.startocare.userservice.dto.response;

import java.util.UUID;

public record CompanionLocationResponse(
        UUID id,
        String serviceLocationAddress,
        double latitude,
        double longitude
) {
}
