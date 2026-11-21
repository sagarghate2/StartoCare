package com.startocare.userservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.userservice.dto.response.CompanionLocationResponse;
import com.startocare.userservice.events.CompanionLocationLookupRequestedEvent;
import com.startocare.userservice.events.CompanionLocationLookupResponseEvent;
import com.startocare.userservice.exception.ResourceNotFoundException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class CompanionLocationLookupListener {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final UserService userService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String internalApiKey;
    private final String responseTopic;

    public CompanionLocationLookupListener(
            UserService userService,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.internal-api-key:}") String internalApiKey,
            @Value("${app.kafka.topics.companion-location-response}") String responseTopic
    ) {
        this.userService = userService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.internalApiKey = internalApiKey;
        this.responseTopic = responseTopic;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.companion-location-request}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onCompanionLocationLookupRequested(ConsumerRecord<String, String> record) {
        CompanionLocationLookupRequestedEvent request;
        try {
            request = objectMapper.readValue(record.value(), CompanionLocationLookupRequestedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid companion-location request payload", exception);
        }
        if (request.requestId() == null || request.companionId() == null) {
            throw new IllegalArgumentException("Companion-location request is missing required identifiers");
        }

        CompanionLocationLookupResponseEvent response;
        try {
            if (!isAuthorized(request)) {
                response = new CompanionLocationLookupResponseEvent(request.requestId(), null, "UNAUTHORIZED");
            } else {
                CompanionLocationResponse location = userService.getVerifiedCompanionLocation(request.companionId());
                response = new CompanionLocationLookupResponseEvent(request.requestId(), location, null);
            }
        } catch (ResourceNotFoundException exception) {
            response = new CompanionLocationLookupResponseEvent(request.requestId(), null, "NOT_FOUND");
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != 404) {
                throw exception;
            }
            response = new CompanionLocationLookupResponseEvent(request.requestId(), null, "NOT_FOUND");
        }

        String responsePayload;
        try {
            responsePayload = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize companion-location response", exception);
        }
        try {
            kafkaTemplate.send(responseTopic, request.requestId().toString(), responsePayload)
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publishing companion-location response was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("Could not publish companion-location response", exception);
        }
    }

    private boolean isAuthorized(CompanionLocationLookupRequestedEvent request) {
        if (internalApiKey.isBlank() || request.signature() == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(internalApiKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] expected = Base64.getUrlEncoder().withoutPadding().encode(
                    mac.doFinal((request.requestId() + "\n" + request.companionId())
                            .getBytes(StandardCharsets.UTF_8))
            );
            return MessageDigest.isEqual(expected, request.signature().getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not validate companion-location request signature", exception);
        }
    }
}
