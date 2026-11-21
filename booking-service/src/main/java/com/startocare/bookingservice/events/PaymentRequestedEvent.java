package com.startocare.bookingservice.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentRequestedEvent(
        UUID eventId,
        UUID bookingId,
        String bookingReference,
        UUID familyUserId,
        long amountPaise,
        String currency,
        Instant occurredAt
) {
}
