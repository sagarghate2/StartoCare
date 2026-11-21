package com.startocare.bookingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.bookingservice.dto.response.CompanionLocationResponse;
import com.startocare.bookingservice.events.CompanionLocationLookupRequestedEvent;
import com.startocare.bookingservice.events.CompanionLocationLookupResponseEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanionLocationClientTest {

    @Test
    void publishesSignedLookupAndReturnsMatchingReply() throws Exception {
        String apiKey = "test-internal-api-key";
        ObjectMapper objectMapper = new ObjectMapper();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        AtomicReference<CompanionLocationClient> clientReference = new AtomicReference<>();
        when(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>any()))
                .thenAnswer(invocation -> {
            ProducerRecord<String, String> requestRecord = invocation.getArgument(0);
            CompanionLocationLookupRequestedEvent request = objectMapper.readValue(
                    requestRecord.value(),
                    CompanionLocationLookupRequestedEvent.class
            );
            CompanionLocationResponse location = new CompanionLocationResponse(
                    request.companionId(),
                    "Hyderabad",
                    17.4,
                    78.5
            );
            clientReference.get().onCompanionLocationLookupResponse(objectMapper.writeValueAsString(
                    new CompanionLocationLookupResponseEvent(request.requestId(), location, null)
            ));
            return CompletableFuture.completedFuture(null);
        });
        CompanionLocationClient client = new CompanionLocationClient(
                kafkaTemplate,
                objectMapper,
                apiKey,
                "companion.location.requested.test",
                1000
        );
        clientReference.set(client);

        UUID companionId = UUID.randomUUID();
        CompanionLocationResponse response = client.getVerifiedLocation(companionId);

        assertEquals(companionId, response.id());
        assertEquals("Hyderabad", response.serviceLocationAddress());
        assertEquals(17.4, response.latitude());
    }
}
