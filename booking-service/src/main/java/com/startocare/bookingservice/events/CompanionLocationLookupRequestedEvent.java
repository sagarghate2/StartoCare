package com.startocare.bookingservice.events;

import java.util.UUID;

public record CompanionLocationLookupRequestedEvent(
        UUID requestId,
        UUID companionId,
        String signature
) {
}
