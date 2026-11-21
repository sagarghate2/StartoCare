package com.startocare.paymentservice.controller;

import com.startocare.paymentservice.dto.PaymentCheckoutResponse;
import com.startocare.paymentservice.dto.VerifyPaymentRequest;
import com.startocare.paymentservice.security.PaymentPrincipal;
import com.startocare.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final String webhookSecret;

    public PaymentController(
            PaymentService paymentService,
            @Value("${app.razorpay.webhook-secret}") String webhookSecret
    ) {
        this.paymentService = paymentService;
        this.webhookSecret = webhookSecret;
    }

    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<PaymentCheckoutResponse> getCheckout(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal PaymentPrincipal principal
    ) {
        return ResponseEntity.ok(paymentService.getCheckout(bookingId, principal));
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentCheckoutResponse> verifyCheckout(
            @AuthenticationPrincipal PaymentPrincipal principal,
            @Valid @RequestBody VerifyPaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.verifyCheckout(request, principal));
    }

    @PostMapping("/razorpay/webhook")
    public ResponseEntity<Void> razorpayWebhook(
            @RequestBody String body,
            @RequestHeader("X-Razorpay-Signature") String signature
    ) {
        paymentService.handleCapturedWebhook(body, signature, webhookSecret);
        return ResponseEntity.ok().build();
    }
}
