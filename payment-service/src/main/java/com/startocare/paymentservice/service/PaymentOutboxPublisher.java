package com.startocare.paymentservice.service;

import com.startocare.paymentservice.entity.OutboxEvent;
import com.startocare.paymentservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentOutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(PaymentOutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String paymentSucceededTopic;

    public PaymentOutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.topics.payment-succeeded}") String paymentSucceededTopic
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.paymentSucceededTopic = paymentSucceededTopic;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox-poll-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        for (OutboxEvent event : outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                kafkaTemplate.send(paymentSucceededTopic, event.getAggregateId().toString(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                event.markPublished();
            } catch (Exception exception) {
                event.recordFailure(exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage());
                logger.error("Could not publish payment outbox event {} (attempt {})",
                        event.getId(), event.getAttempts(), exception);
            }
        }
    }
}
