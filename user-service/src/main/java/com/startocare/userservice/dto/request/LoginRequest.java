package com.startocare.userservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254)
        String email,

        @NotBlank(message = "Password is required")
        String password
) {

    public LoginRequest {
        if (email != null) {
            email = email.trim();
        }
    }
}
