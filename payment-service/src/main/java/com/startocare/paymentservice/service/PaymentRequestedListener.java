package com.startocare.paymentservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.paymentservice.events.PaymentRequestedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentRequestedListener {

    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    public PaymentRequestedListener(ObjectMapper objectMapper, PaymentService paymentService) {
        this.objectMapper = objectMapper;
        this.paymentService = paymentService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.payment-requested}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPaymentRequested(String payload) {
        try {
            paymentService.createOrderForBooking(objectMapper.readValue(payload, PaymentRequestedEvent.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid payment-requested event payload", exception);
        }
    }
}
