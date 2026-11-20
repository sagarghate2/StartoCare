package com.startocare.bookingservice.repository;

import com.startocare.bookingservice.entity.ServiceDurationPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceDurationPriceRepository extends JpaRepository<ServiceDurationPrice, UUID> {
    Optional<ServiceDurationPrice> findByDurationMinutesAndActiveTrue(Integer durationMinutes);
    List<ServiceDurationPrice> findByActiveTrueOrderByDurationMinutesAsc();
}
