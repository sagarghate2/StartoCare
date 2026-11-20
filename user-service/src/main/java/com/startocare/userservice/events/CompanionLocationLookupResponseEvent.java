package com.startocare.userservice.events;

import com.startocare.userservice.dto.response.CompanionLocationResponse;

import java.util.UUID;

public record CompanionLocationLookupResponseEvent(
        UUID requestId,
        CompanionLocationResponse location,
        String errorCode
) {
}
