package com.startocare.bookingservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.bookingservice.entity.Booking;
import com.startocare.bookingservice.entity.OutboxEvent;
import com.startocare.bookingservice.events.PaymentRequestedEvent;
import com.startocare.bookingservice.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
public class BookingEventOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public BookingEventOutboxService(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public void queuePaymentRequest(Booking booking) {
        long amountPaise = booking.getTotalAmount()
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
        PaymentRequestedEvent event = new PaymentRequestedEvent(
                UUID.randomUUID(),
                booking.getId(),
                booking.getBookingReference(),
                booking.getFamilyUserId(),
                amountPaise,
                "INR",
                Instant.now()
        );
        try {
            outboxEventRepository.save(new OutboxEvent(
                    booking.getId(),
                    "PaymentRequested",
                    objectMapper.writeValueAsString(event)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize payment request event", exception);
        }
    }
}
