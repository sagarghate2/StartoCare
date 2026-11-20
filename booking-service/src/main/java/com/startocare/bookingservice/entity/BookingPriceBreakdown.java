package com.startocare.bookingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "booking_price_breakdowns",
        indexes = @Index(name = "idx_booking_breakdowns_booking_id", columnList = "booking_id")
)
public class BookingPriceBreakdown {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "service_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal serviceAmount;

    @Column(name = "travel_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal travelAmount;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 10)
    private String currency = "INR";

    @Column(name = "quoted_at", nullable = false)
    private Instant quotedAt;

    public BookingPriceBreakdown() {
    }

    public UUID getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public BigDecimal getServiceAmount() {
        return serviceAmount;
    }

    public void setServiceAmount(BigDecimal serviceAmount) {
        this.serviceAmount = serviceAmount;
    }

    public BigDecimal getTravelAmount() {
        return travelAmount;
    }

    public void setTravelAmount(BigDecimal travelAmount) {
        this.travelAmount = travelAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getQuotedAt() {
        return quotedAt;
    }

    public void setQuotedAt(Instant quotedAt) {
        this.quotedAt = quotedAt;
    }

    @PrePersist
    public void onCreate() {
        if (quotedAt == null) {
            quotedAt = Instant.now();
        }
    }
}
