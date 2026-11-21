package com.startocare.paymentservice.repository;

import com.startocare.paymentservice.entity.PaymentOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {
    Optional<PaymentOrder> findByBookingId(UUID bookingId);
    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentOrder payment where payment.razorpayOrderId = :orderId")
    Optional<PaymentOrder> findByRazorpayOrderIdForUpdate(@Param("orderId") String orderId);
}
