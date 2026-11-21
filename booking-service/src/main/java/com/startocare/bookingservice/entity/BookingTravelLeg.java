package com.startocare.bookingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_travel_legs")
public class BookingTravelLeg {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "leg_order", nullable = false)
    private Integer legOrder;

    @Column(name = "leg_type", nullable = false, length = 40)
    private String legType;

    @Column(name = "origin_label", nullable = false, length = 80)
    private String originLabel;

    @Column(name = "destination_label", nullable = false, length = 80)
    private String destinationLabel;

    @Column(name = "distance_meters", nullable = false)
    private Long distanceMeters;

    @Column(name = "distance_km", nullable = false, precision = 10, scale = 3)
    private BigDecimal distanceKm;

    @Column(name = "base_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "distance_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceCharge;

    @Column(name = "total_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalFare;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public BookingTravelLeg() {
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public void setLegOrder(Integer legOrder) {
        this.legOrder = legOrder;
    }

    public void setLegType(String legType) {
        this.legType = legType;
    }

    public void setOriginLabel(String originLabel) {
        this.originLabel = originLabel;
    }

    public void setDestinationLabel(String destinationLabel) {
        this.destinationLabel = destinationLabel;
    }

    public void setDistanceMeters(Long distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public void setBaseFare(BigDecimal baseFare) {
        this.baseFare = baseFare;
    }

    public void setDistanceCharge(BigDecimal distanceCharge) {
        this.distanceCharge = distanceCharge;
    }

    public void setTotalFare(BigDecimal totalFare) {
        this.totalFare = totalFare;
    }

    public Integer getLegOrder() {
        return legOrder;
    }

    public String getLegType() {
        return legType;
    }

    public String getOriginLabel() {
        return originLabel;
    }

    public String getDestinationLabel() {
        return destinationLabel;
    }

    public Long getDistanceMeters() {
        return distanceMeters;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public BigDecimal getBaseFare() {
        return baseFare;
    }

    public BigDecimal getDistanceCharge() {
        return distanceCharge;
    }

    public BigDecimal getTotalFare() {
        return totalFare;
    }

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
