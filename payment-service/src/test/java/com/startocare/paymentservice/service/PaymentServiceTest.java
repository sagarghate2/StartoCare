package com.startocare.paymentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.paymentservice.dto.VerifyPaymentRequest;
import com.startocare.paymentservice.entity.OutboxEvent;
import com.startocare.paymentservice.entity.PaymentOrder;
import com.startocare.paymentservice.enums.PaymentStatus;
import com.startocare.paymentservice.enums.Role;
import com.startocare.paymentservice.events.PaymentRequestedEvent;
import com.startocare.paymentservice.repository.OutboxEventRepository;
import com.startocare.paymentservice.repository.PaymentOrderRepository;
import com.startocare.paymentservice.security.PaymentPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class PaymentServiceTest {

    private static final String KEY_SECRET = "test-key-secret";
    private static final UUID BOOKING_ID = UUID.randomUUID();
    private static final UUID FAMILY_ID = UUID.randomUUID();
    private static final String ORDER_ID = "order_test_123";
    private static final String PAYMENT_ID = "pay_test_456";

    private PaymentOrderRepository paymentOrderRepository;
    private OutboxEventRepository outboxEventRepository;
    private RazorpayGateway razorpayGateway;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentOrderRepository = mock(PaymentOrderRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        razorpayGateway = mock(RazorpayGateway.class);
        when(razorpayGateway.keyId()).thenReturn("rzp_test_id");
        paymentService = new PaymentService(
                paymentOrderRepository,
                outboxEventRepository,
                razorpayGateway,
                new ObjectMapper().findAndRegisterModules(),
                KEY_SECRET
        );
    }

    @Test
    void duplicateBookingRequestDoesNotCreateAnotherRazorpayOrder() {
        PaymentOrder existing = new PaymentOrder(
                BOOKING_ID, FAMILY_ID, "SC-123", 25000, "INR", ORDER_ID
        );
        when(paymentOrderRepository.findByBookingId(BOOKING_ID)).thenReturn(Optional.of(existing));

        paymentService.createOrderForBooking(new PaymentRequestedEvent(
                UUID.randomUUID(), BOOKING_ID, "SC-123", FAMILY_ID, 25000, "INR", Instant.now()
        ));

        verify(razorpayGateway, never()).createOrder(anyLong(), any(), any(), any());
        verify(paymentOrderRepository, never()).save(any(PaymentOrder.class));
    }

    @Test
    void invalidCheckoutSignatureDoesNotQueryRazorpay() {
        PaymentOrder order = new PaymentOrder(
                BOOKING_ID, FAMILY_ID, "SC-123", 25000, "INR", ORDER_ID
        );
        when(paymentOrderRepository.findByRazorpayOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(ResponseStatusException.class, () -> paymentService.verifyCheckout(
                new VerifyPaymentRequest(ORDER_ID, PAYMENT_ID, "invalid"),
                new PaymentPrincipal(FAMILY_ID, Role.FAMILY)
        ));

        verify(razorpayGateway, never()).getPayment(PAYMENT_ID);
    }

    @Test
    void capturedCheckoutPaymentStoresPaidStateAndSuccessOutboxEvent() throws Exception {
        PaymentOrder order = new PaymentOrder(
                BOOKING_ID, FAMILY_ID, "SC-123", 25000, "INR", ORDER_ID
        );
        when(paymentOrderRepository.findByRazorpayOrderIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(razorpayGateway.getPayment(PAYMENT_ID)).thenReturn(new RazorpayGateway.CapturedPayment(
                PAYMENT_ID, ORDER_ID, 25000, "INR", "captured"
        ));

        var response = paymentService.verifyCheckout(
                new VerifyPaymentRequest(ORDER_ID, PAYMENT_ID, signature(ORDER_ID + "|" + PAYMENT_ID)),
                new PaymentPrincipal(FAMILY_ID, Role.FAMILY)
        );

        assertEquals(PaymentStatus.PAID, response.paymentStatus());
        assertEquals(PAYMENT_ID, order.getRazorpayPaymentId());
        verify(paymentOrderRepository).save(order);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    private String signature(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
