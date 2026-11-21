package com.startocare.bookingservice.dto.response;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String path,
        Map<String, Object> details
) {
}
