package com.startocare.bookingservice.events;

import com.startocare.bookingservice.dto.response.CompanionLocationResponse;

import java.util.UUID;

public record CompanionLocationLookupResponseEvent(
        UUID requestId,
        CompanionLocationResponse location,
        String errorCode
) {
}
