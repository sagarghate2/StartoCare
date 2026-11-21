package com.startocare.userservice.dto.response;

import java.util.UUID;

public record CompanionSearchResponse(
        UUID id,
        String fullName,
        String service,
        double distanceKm
) {
}
