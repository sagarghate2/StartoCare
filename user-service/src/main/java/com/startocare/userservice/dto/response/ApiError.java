package com.startocare.userservice.dto.response;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String path,
        Map<String, String> details
) {
}
