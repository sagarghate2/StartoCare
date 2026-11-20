package com.startocare.userservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.userservice.dto.response.CompanionLocationResponse;
import com.startocare.userservice.events.CompanionLocationLookupRequestedEvent;
import com.startocare.userservice.events.CompanionLocationLookupResponseEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionLocationLookupListenerTest {

    private static final String API_KEY = "test-internal-api-key";
    private static final String RESPONSE_TOPIC = "companion.location.response.test";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserService userService = mock(UserService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private CompanionLocationLookupListener listener;

    @BeforeEach
    void setUp() {
        listener = new CompanionLocationLookupListener(
                userService,
                kafkaTemplate,
                objectMapper,
                API_KEY,
                RESPONSE_TOPIC
        );
        when(kafkaTemplate.send(eq(RESPONSE_TOPIC), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void verifiesAndRepliesWithCompanionLocation() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID companionId = UUID.randomUUID();
        CompanionLocationResponse location =
                new CompanionLocationResponse(companionId, "Hyderabad", 17.4, 78.5);
        when(userService.getVerifiedCompanionLocation(companionId)).thenReturn(location);

        listener.onCompanionLocationLookupRequested(requestRecord(
                new CompanionLocationLookupRequestedEvent(requestId, companionId, sign(requestId, companionId))
        ));

        verify(userService).getVerifiedCompanionLocation(companionId);
        var payloadCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(RESPONSE_TOPIC), eq(requestId.toString()), payloadCaptor.capture());
        CompanionLocationLookupResponseEvent response =
                objectMapper.readValue(payloadCaptor.getValue(), CompanionLocationLookupResponseEvent.class);
        assertEquals(requestId, response.requestId());
        assertEquals(location, response.location());
        assertNull(response.errorCode());
    }

    @Test
    void doesNotLookUpLocationWhenSignatureIsInvalid() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID companionId = UUID.randomUUID();

        listener.onCompanionLocationLookupRequested(
                requestRecord(new CompanionLocationLookupRequestedEvent(requestId, companionId, "invalid"))
        );

        verify(userService, never()).getVerifiedCompanionLocation(companionId);
        var payloadCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(RESPONSE_TOPIC), eq(requestId.toString()), payloadCaptor.capture());
        CompanionLocationLookupResponseEvent response =
                objectMapper.readValue(payloadCaptor.getValue(), CompanionLocationLookupResponseEvent.class);
        assertEquals("UNAUTHORIZED", response.errorCode());
    }

    private ConsumerRecord<String, String> requestRecord(CompanionLocationLookupRequestedEvent event)
            throws Exception {
        return new ConsumerRecord<>(
                "companion.location.requested.test",
                0,
                0L,
                event.companionId().toString(),
                objectMapper.writeValueAsString(event)
        );
    }

    private String sign(UUID requestId, UUID companionId) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(API_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal((requestId + "\n" + companionId).getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }
}
