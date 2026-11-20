package com.startocare.bookingservice.repository;

import com.startocare.bookingservice.entity.Booking;
import com.startocare.bookingservice.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByBookingReference(String bookingReference);
    Page<Booking> findByFamilyUserIdOrderByCreatedAtDesc(UUID familyUserId, Pageable pageable);
    Page<Booking> findByCompanionIdAndBookingStatusOrderByCreatedAtDesc(
            UUID companionId,
            BookingStatus bookingStatus,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") UUID bookingId);
}
