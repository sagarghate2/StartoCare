package com.startocare.notificationservice.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentRequestedEvent(
        UUID eventId,
        UUID bookingId,
        UUID familyUserId,
        long amountPaise,
        String currency,
        Instant occurredAt
) {
}
