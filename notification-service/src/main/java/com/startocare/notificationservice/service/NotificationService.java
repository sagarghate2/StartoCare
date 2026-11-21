package com.startocare.notificationservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.notificationservice.events.PaymentRequestedEvent;
import com.startocare.notificationservice.events.PaymentSucceededEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final SnsClient snsClient;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String paymentTopicArn;
    private final String bookingTopicArn;
    private final String queueUrl;

    public NotificationService(
            SnsClient snsClient,
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            @Value("${aws.sns.payment-topic-arn:}") String paymentTopicArn,
            @Value("${aws.sns.booking-topic-arn:}") String bookingTopicArn,
            @Value("${aws.sqs.queue-url:}") String queueUrl
    ) {
        this.snsClient = snsClient;
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.paymentTopicArn = paymentTopicArn;
        this.bookingTopicArn = bookingTopicArn;
        this.queueUrl = queueUrl;
    }

    public void handlePaymentRequested(PaymentRequestedEvent event) {
        String message = buildSubjectAndMessage(
                "Payment requested",
                "A payment request has been created for booking " + event.bookingId() + ".",
                event
        );
        publishToSns(paymentTopicArn, "Payment requested", message, Map.of(
                "eventType", "payment.requested",
                "bookingId", event.bookingId().toString(),
                "userId", event.familyUserId().toString()
        ));
        enqueueInSqs("payment.requested", message, Map.of(
                "eventType", "payment.requested",
                "bookingId", event.bookingId().toString(),
                "userId", event.familyUserId().toString()
        ));
    }

    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        String message = buildSubjectAndMessage(
                "Payment successful",
                "Payment for booking " + event.bookingId() + " has been completed successfully.",
                event
        );
        publishToSns(paymentTopicArn, "Payment successful", message, Map.of(
                "eventType", "payment.succeeded",
                "bookingId", event.bookingId().toString(),
                "userId", event.familyUserId().toString()
        ));
        enqueueInSqs("payment.succeeded", message, Map.of(
                "eventType", "payment.succeeded",
                "bookingId", event.bookingId().toString(),
                "userId", event.familyUserId().toString()
        ));
    }

    private String buildSubjectAndMessage(String title, String summary, Object payload) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("title", title);
        content.put("summary", summary);
        content.put("payload", payload);
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            logger.warn("Could not serialize notification payload", exception);
            return summary;
        }
    }

    private void publishToSns(String topicArn, String subject, String message, Map<String, String> attributes) {
        if (topicArn == null || topicArn.isBlank()) {
            logger.debug("Skipping SNS publish for {} because no topic ARN is configured", subject);
            return;
        }

        Map<String, MessageAttributeValue> snsAttributes = new LinkedHashMap<>();
        attributes.forEach((key, value) -> snsAttributes.put(key,
                MessageAttributeValue.builder().dataType("String").stringValue(value).build()));

        snsClient.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .subject(subject)
                .message(message)
                .messageAttributes(snsAttributes)
                .build());
    }

    private void enqueueInSqs(String messageGroup, String messageBody, Map<String, String> attributes) {
        if (queueUrl == null || queueUrl.isBlank()) {
            logger.debug("Skipping SQS enqueue for {} because no queue URL is configured", messageGroup);
            return;
        }

        Map<String, software.amazon.awssdk.services.sqs.model.MessageAttributeValue> sqsAttributes = new LinkedHashMap<>();
        attributes.forEach((key, value) -> sqsAttributes.put(key,
                software.amazon.awssdk.services.sqs.model.MessageAttributeValue.builder().dataType("String").stringValue(value).build()));

        SendMessageRequest.Builder requestBuilder = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .messageAttributes(sqsAttributes);

        if (queueUrl.toLowerCase(Locale.ROOT).endsWith(".fifo")) {
            requestBuilder.messageGroupId(messageGroup);
        }

        sqsClient.sendMessage(requestBuilder.build());
    }
}
