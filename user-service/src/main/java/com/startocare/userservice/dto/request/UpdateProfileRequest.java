package com.startocare.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must not exceed 120 characters")
        String fullName,

        @Pattern(regexp = "\\+91[6-9][0-9]{9}", message = "Phone number must be a valid Indian number in +91 format")
        String phoneNumber
) {
}
