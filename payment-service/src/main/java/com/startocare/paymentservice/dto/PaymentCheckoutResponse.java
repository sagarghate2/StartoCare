package com.startocare.paymentservice.dto;

import com.startocare.paymentservice.enums.PaymentStatus;

import java.util.UUID;

public record PaymentCheckoutResponse(
        UUID bookingId,
        String razorpayKeyId,
        String razorpayOrderId,
        long amountPaise,
        String currency,
        PaymentStatus paymentStatus
) {
}
