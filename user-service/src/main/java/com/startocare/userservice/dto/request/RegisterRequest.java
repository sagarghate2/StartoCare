package com.startocare.userservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must not exceed 120 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "\\+91[6-9][0-9]{9}", message = "Phone number must be a valid Indian number in +91 format")
        String phoneNumber,

        @NotBlank(message = "Password is required")
        @ValidPassword
        String password,

        String otp
) {
    public RegisterRequest {
        if (email != null) {
            email = email.trim();
        }
        if (phoneNumber != null) {
            phoneNumber = phoneNumber.trim();
        }
    }
}
