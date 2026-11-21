package com.startocare.userservice.dto.response;

import com.startocare.userservice.entity.User;
import com.startocare.userservice.enums.CompanionVerificationStatus;

import java.util.UUID;

public record CompanionApplicationResponse(
        UUID id,
        String fullName,
        String serviceLocationAddress,
        Double latitude,
        Double longitude,
        CompanionVerificationStatus verificationStatus
) {
    public static CompanionApplicationResponse from(User user) {
        return new CompanionApplicationResponse(
                user.getId(),
                user.getFullName(),
                user.getServiceLocationAddress(),
                user.getServiceLatitude(),
                user.getServiceLongitude(),
                user.getCompanionVerificationStatus()
        );
    }
}
