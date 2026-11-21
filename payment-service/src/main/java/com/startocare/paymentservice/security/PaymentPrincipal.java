package com.startocare.paymentservice.security;

import com.startocare.paymentservice.enums.Role;

import java.util.UUID;

public record PaymentPrincipal(UUID userId, Role role) {
}
