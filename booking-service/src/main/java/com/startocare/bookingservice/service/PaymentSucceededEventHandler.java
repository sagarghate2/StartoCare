package com.startocare.bookingservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.bookingservice.entity.Booking;
import com.startocare.bookingservice.enums.BookingStatus;
import com.startocare.bookingservice.events.PaymentSucceededEvent;
import com.startocare.bookingservice.exception.BookingNotFoundException;
import com.startocare.bookingservice.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;

@Component
public class PaymentSucceededEventHandler {

    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper;

    public PaymentSucceededEventHandler(
            BookingRepository bookingRepository,
            ObjectMapper objectMapper
    ) {
        this.bookingRepository = bookingRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.payment-succeeded}",
            groupId = "${spring.kafka.consumer.group-id}",
            autoStartup = "${app.kafka.enabled:true}"
    )
    @Transactional
    public void onPaymentSucceeded(String payload) {
        PaymentSucceededEvent event;
        try {
            event = objectMapper.readValue(payload, PaymentSucceededEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid payment-succeeded event payload", exception);
        }
        Booking booking = bookingRepository.findByIdForUpdate(event.bookingId())
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + event.bookingId()));
        if (booking.getBookingStatus() != BookingStatus.AWAITING_PAYMENT) {
            return;
        }
        long expectedAmountPaise = booking.getTotalAmount()
                .movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
        if (!booking.getFamilyUserId().equals(event.familyUserId())
                || expectedAmountPaise != event.amountPaise()
                || !"INR".equals(event.currency())
                || event.razorpayOrderId() == null
                || event.razorpayPaymentId() == null) {
            throw new IllegalArgumentException("Payment event does not match the booking amount or owner");
        }
        booking.setBookingStatus(BookingStatus.PENDING);
        bookingRepository.save(booking);
    }
}
