package com.startocare.bookingservice.repository;

import com.startocare.bookingservice.entity.BookingPriceBreakdown;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookingPriceBreakdownRepository extends JpaRepository<BookingPriceBreakdown, UUID> {
}
