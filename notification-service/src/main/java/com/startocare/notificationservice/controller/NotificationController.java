package com.startocare.notificationservice.controller;

import com.startocare.notificationservice.events.PaymentSucceededEvent;
import com.startocare.notificationservice.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/test-payment-success")
    public ResponseEntity<Map<String, String>> pushTestPaymentSuccess(@RequestBody Map<String, String> payload) {
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                UUID.randomUUID(),
                UUID.fromString(payload.getOrDefault("bookingId", UUID.randomUUID().toString())),
                UUID.fromString(payload.getOrDefault("familyUserId", UUID.randomUUID().toString())),
                payload.getOrDefault("razorpayOrderId", "order_test"),
                payload.getOrDefault("razorpayPaymentId", "pay_test"),
                Long.parseLong(payload.getOrDefault("amountPaise", "10000")),
                payload.getOrDefault("currency", "INR"),
                Instant.now()
        );
        notificationService.handlePaymentSucceeded(event);
        return ResponseEntity.accepted().body(Map.of("status", "queued"));
    }
}
