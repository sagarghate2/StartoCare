package com.startocare.userservice.dto.request;

import com.startocare.userservice.enums.CompanionVerificationStatus;
import jakarta.validation.constraints.NotNull;

public record CompanionVerificationRequest(
        @NotNull CompanionVerificationStatus status
) {
}
