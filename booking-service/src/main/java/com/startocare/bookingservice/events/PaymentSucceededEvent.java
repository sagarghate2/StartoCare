package com.startocare.bookingservice.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId,
        UUID bookingId,
        UUID familyUserId,
        String razorpayOrderId,
        String razorpayPaymentId,
        long amountPaise,
        String currency,
        Instant occurredAt
) {
}
