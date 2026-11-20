package com.startocare.bookingservice.service;

import com.startocare.bookingservice.entity.OutboxEvent;
import com.startocare.bookingservice.repository.OutboxEventRepository;
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
public class BookingOutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(BookingOutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String paymentRequestedTopic;

    public BookingOutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.kafka.topics.payment-requested}") String paymentRequestedTopic
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.paymentRequestedTopic = paymentRequestedTopic;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox-poll-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        for (OutboxEvent event : outboxEventRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                kafkaTemplate.send(paymentRequestedTopic, event.getAggregateId().toString(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                event.markPublished();
            } catch (Exception exception) {
                event.recordFailure(exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage());
                logger.error("Could not publish booking outbox event {} (attempt {})",
                        event.getId(), event.getAttempts(), exception);
            }
        }
    }
}
