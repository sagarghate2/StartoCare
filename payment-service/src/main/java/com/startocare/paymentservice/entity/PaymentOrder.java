package com.startocare.paymentservice.entity;

import com.startocare.paymentservice.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_orders")
public class PaymentOrder {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Column(name = "family_user_id", nullable = false)
    private UUID familyUserId;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 32)
    private String bookingReference;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", unique = true, length = 64)
    private String razorpayPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 24)
    private PaymentStatus paymentStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentOrder() {
    }

    public PaymentOrder(
            UUID bookingId,
            UUID familyUserId,
            String bookingReference,
            long amountPaise,
            String currency,
            String razorpayOrderId
    ) {
        this.bookingId = bookingId;
        this.familyUserId = familyUserId;
        this.bookingReference = bookingReference;
        this.amountPaise = amountPaise;
        this.currency = currency;
        this.razorpayOrderId = razorpayOrderId;
        this.paymentStatus = PaymentStatus.ORDER_CREATED;
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public UUID getFamilyUserId() { return familyUserId; }
    public String getBookingReference() { return bookingReference; }
    public long getAmountPaise() { return amountPaise; }
    public String getCurrency() { return currency; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }

    public void markPaid(String razorpayPaymentId) {
        this.razorpayPaymentId = razorpayPaymentId;
        this.paymentStatus = PaymentStatus.PAID;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
