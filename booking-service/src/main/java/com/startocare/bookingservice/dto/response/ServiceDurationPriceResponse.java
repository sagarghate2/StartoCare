package com.startocare.bookingservice.dto.response;

import java.math.BigDecimal;

public record ServiceDurationPriceResponse(
        Integer durationMinutes,
        BigDecimal serviceAmount,
        Boolean active
) {
}
