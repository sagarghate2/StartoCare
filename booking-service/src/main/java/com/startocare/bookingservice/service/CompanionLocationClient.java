package com.startocare.bookingservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.bookingservice.dto.response.CompanionLocationResponse;
import com.startocare.bookingservice.events.CompanionLocationLookupRequestedEvent;
import com.startocare.bookingservice.events.CompanionLocationLookupResponseEvent;
import com.startocare.bookingservice.exception.EstimateUnavailableException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class CompanionLocationClient {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String requestTopic;
    private final long responseTimeoutMs;
    private final ConcurrentHashMap<UUID, CompletableFuture<CompanionLocationLookupResponseEvent>> pendingRequests =
            new ConcurrentHashMap<>();

    public CompanionLocationClient(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.companion-api-key:}") String apiKey,
            @Value("${app.kafka.topics.companion-location-request}") String requestTopic,
            @Value("${app.kafka.location-response-timeout-ms:5000}") long responseTimeoutMs
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.requestTopic = requestTopic;
        this.responseTimeoutMs = responseTimeoutMs;
    }

    public CompanionLocationResponse getVerifiedLocation(UUID companionId) {
        if (apiKey.isBlank()) {
            throw new EstimateUnavailableException("Companion location lookup is not configured");
        }

        UUID requestId = UUID.randomUUID();
        CompletableFuture<CompanionLocationLookupResponseEvent> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);
        try {
            CompanionLocationLookupRequestedEvent request =
                    new CompanionLocationLookupRequestedEvent(requestId, companionId, sign(requestId, companionId));
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    requestTopic,
                    companionId.toString(),
                    objectMapper.writeValueAsString(request)
            );
            kafkaTemplate.send(record).whenComplete((result, exception) -> {
                if (exception != null) {
                    responseFuture.completeExceptionally(exception);
                }
            });

            CompanionLocationLookupResponseEvent reply = responseFuture.get(responseTimeoutMs, TimeUnit.MILLISECONDS);
            if ("NOT_FOUND".equals(reply.errorCode())) {
                throw new EstimateUnavailableException("The selected companion is not available");
            }
            if ("UNAUTHORIZED".equals(reply.errorCode())) {
                throw new EstimateUnavailableException("Companion location lookup authorization failed");
            }
            if (reply.location() == null) {
                throw new EstimateUnavailableException("Companion location could not be loaded");
            }
            return reply.location();
        } catch (JsonProcessingException | GeneralSecurityException exception) {
            throw new EstimateUnavailableException("Companion location request could not be prepared", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EstimateUnavailableException("Companion location lookup was interrupted", exception);
        } catch (TimeoutException exception) {
            throw new EstimateUnavailableException("Companion location lookup timed out", exception);
        } catch (ExecutionException exception) {
            throw new EstimateUnavailableException("Companion location service could not be reached",
                    exception.getCause());
        } catch (RuntimeException exception) {
            if (exception instanceof EstimateUnavailableException estimateUnavailableException) {
                throw estimateUnavailableException;
            }
            throw new EstimateUnavailableException("Companion location service could not be reached", exception);
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.companion-location-response}",
            groupId = "${spring.kafka.consumer.group-id}-location-replies-${random.uuid}",
            autoStartup = "${app.kafka.enabled:true}",
            properties = "auto.offset.reset=latest"
    )
    public void onCompanionLocationLookupResponse(String payload) {
        CompanionLocationLookupResponseEvent response;
        try {
            response = objectMapper.readValue(payload, CompanionLocationLookupResponseEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid companion-location response payload", exception);
        }
        if (response.requestId() == null) {
            throw new IllegalArgumentException("Companion-location response is missing its request ID");
        }
        CompletableFuture<CompanionLocationLookupResponseEvent> responseFuture =
                pendingRequests.get(response.requestId());
        if (responseFuture != null) {
            responseFuture.complete(response);
        }
    }

    private String sign(UUID requestId, UUID companionId) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        byte[] signature = mac.doFinal((requestId + "\n" + companionId).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }
}
