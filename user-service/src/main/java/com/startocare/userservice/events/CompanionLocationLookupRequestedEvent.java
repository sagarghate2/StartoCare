package com.startocare.userservice.events;

import java.util.UUID;

public record CompanionLocationLookupRequestedEvent(
        UUID requestId,
        UUID companionId,
        String signature
) {
}
