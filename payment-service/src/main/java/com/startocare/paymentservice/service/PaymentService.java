package com.startocare.paymentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.paymentservice.dto.PaymentCheckoutResponse;
import com.startocare.paymentservice.dto.VerifyPaymentRequest;
import com.startocare.paymentservice.entity.OutboxEvent;
import com.startocare.paymentservice.entity.PaymentOrder;
import com.startocare.paymentservice.enums.PaymentStatus;
import com.startocare.paymentservice.enums.Role;
import com.startocare.paymentservice.events.PaymentRequestedEvent;
import com.startocare.paymentservice.events.PaymentSucceededEvent;
import com.startocare.paymentservice.repository.OutboxEventRepository;
import com.startocare.paymentservice.repository.PaymentOrderRepository;
import com.startocare.paymentservice.security.PaymentPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RazorpayGateway razorpayGateway;
    private final ObjectMapper objectMapper;
    private final String keySecret;

    public PaymentService(
            PaymentOrderRepository paymentOrderRepository,
            OutboxEventRepository outboxEventRepository,
            RazorpayGateway razorpayGateway,
            ObjectMapper objectMapper,
            @Value("${app.razorpay.key-secret}") String keySecret
    ) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.razorpayGateway = razorpayGateway;
        this.objectMapper = objectMapper;
        this.keySecret = keySecret;
    }

    @Transactional
    public void createOrderForBooking(PaymentRequestedEvent event) {
        if (event.amountPaise() < 100 || !"INR".equals(event.currency())) {
            throw new IllegalArgumentException("Payment request must be at least INR 1.00 and use INR");
        }
        PaymentOrder existing = paymentOrderRepository.findByBookingId(event.bookingId()).orElse(null);
        if (existing != null) {
            if (!existing.getFamilyUserId().equals(event.familyUserId())
                    || existing.getAmountPaise() != event.amountPaise()) {
                throw new IllegalArgumentException("Duplicate booking payment request conflicts with the existing order");
            }
            return;
        }

        String orderId = razorpayGateway.createOrder(
                event.amountPaise(),
                event.currency(),
                event.bookingReference(),
                event.bookingId().toString()
        );
        paymentOrderRepository.save(new PaymentOrder(
                event.bookingId(),
                event.familyUserId(),
                event.bookingReference(),
                event.amountPaise(),
                event.currency(),
                orderId
        ));
    }

    @Transactional(readOnly = true)
    public PaymentCheckoutResponse getCheckout(UUID bookingId, PaymentPrincipal principal) {
        requireFamily(principal);
        PaymentOrder order = paymentOrderRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment order is not ready yet; retry shortly"
                ));
        if (!order.getFamilyUserId().equals(principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot access this payment");
        }
        return toResponse(order);
    }

    @Transactional
    public PaymentCheckoutResponse verifyCheckout(VerifyPaymentRequest request, PaymentPrincipal principal) {
        requireFamily(principal);
        PaymentOrder order = paymentOrderRepository.findByRazorpayOrderIdForUpdate(request.razorpayOrderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found"));
        if (!order.getFamilyUserId().equals(principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot verify another user's payment");
        }
        if (!isValidCheckoutSignature(request)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay payment signature is invalid");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            return toResponse(order);
        }

        RazorpayGateway.CapturedPayment payment = razorpayGateway.getPayment(request.razorpayPaymentId());
        if (!payment.paymentId().equals(request.razorpayPaymentId())
                || !payment.orderId().equals(order.getRazorpayOrderId())
                || payment.amountPaise() != order.getAmountPaise()
                || !payment.currency().equals(order.getCurrency())
                || !"captured".equals(payment.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Razorpay payment is not captured or does not match the order"
            );
        }
        markPaid(order, payment.paymentId());
        return toResponse(order);
    }

    @Transactional
    public void handleCapturedWebhook(String body, String signature, String webhookSecret) {
        if (!constantTimeEquals(hmacSha256(body, webhookSecret), signature)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay webhook signature is invalid");
        }
        JsonNode event;
        try {
            event = objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Razorpay webhook JSON", exception);
        }
        if (!"payment.captured".equals(event.path("event").asText())) {
            return;
        }

        JsonNode paymentEntity = event.path("payload").path("payment").path("entity");
        String orderId = paymentEntity.path("order_id").asText();
        String paymentId = paymentEntity.path("id").asText();
        long amountPaise = paymentEntity.path("amount").asLong(-1);
        String currency = paymentEntity.path("currency").asText();
        String status = paymentEntity.path("status").asText();
        if (orderId.isBlank() || paymentId.isBlank() || !"captured".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay webhook has no captured payment");
        }
        PaymentOrder order = paymentOrderRepository.findByRazorpayOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order not found"));
        if (amountPaise != order.getAmountPaise() || !currency.equals(order.getCurrency())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Captured payment amount does not match its order");
        }
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            markPaid(order, paymentId);
        }
    }

    private void markPaid(PaymentOrder order, String paymentId) {
        order.markPaid(paymentId);
        paymentOrderRepository.save(order);
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                UUID.randomUUID(),
                order.getBookingId(),
                order.getFamilyUserId(),
                order.getRazorpayOrderId(),
                paymentId,
                order.getAmountPaise(),
                order.getCurrency(),
                Instant.now()
        );
        try {
            outboxEventRepository.save(new OutboxEvent(
                    order.getBookingId(),
                    "PaymentSucceeded",
                    objectMapper.writeValueAsString(event)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize payment success event", exception);
        }
    }

    private boolean isValidCheckoutSignature(VerifyPaymentRequest request) {
        String expected = hmacSha256(
                request.razorpayOrderId() + "|" + request.razorpayPaymentId(),
                keySecret
        );
        return constantTimeEquals(expected, request.razorpaySignature());
    }

    private String hmacSha256(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate Razorpay signature", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private void requireFamily(PaymentPrincipal principal) {
        if (principal.role() != Role.FAMILY) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only family users may access booking payments");
        }
    }

    private PaymentCheckoutResponse toResponse(PaymentOrder order) {
        return new PaymentCheckoutResponse(
                order.getBookingId(),
                razorpayGateway.keyId(),
                order.getRazorpayOrderId(),
                order.getAmountPaise(),
                order.getCurrency(),
                order.getPaymentStatus()
        );
    }
}
