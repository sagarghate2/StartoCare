package com.startocare.bookingservice.repository;

import com.startocare.bookingservice.entity.TravelPricingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelPricingConfigRepository extends JpaRepository<TravelPricingConfig, Integer> {
}
