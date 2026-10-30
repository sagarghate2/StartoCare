package com.startocare.userservice.dto.response;

import com.startocare.userservice.entity.User;
import com.startocare.userservice.enums.CompanionVerificationStatus;
import com.startocare.userservice.enums.Role;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String phoneNumber,
        Role role,
        CompanionVerificationStatus companionVerificationStatus,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getCompanionVerificationStatus(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
