package com.startocare.notificationservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.notificationservice.events.PaymentRequestedEvent;
import com.startocare.notificationservice.events.PaymentSucceededEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEventListener {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public NotificationEventListener(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.payment-requested}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPaymentRequested(String payload) {
        try {
            notificationService.handlePaymentRequested(objectMapper.readValue(payload, PaymentRequestedEvent.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid payment-requested event payload", exception);
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.payment-succeeded}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPaymentSucceeded(String payload) {
        try {
            notificationService.handlePaymentSucceeded(objectMapper.readValue(payload, PaymentSucceededEvent.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid payment-succeeded event payload", exception);
        }
    }
}
