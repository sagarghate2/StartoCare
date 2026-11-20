package com.startocare.bookingservice;

import com.startocare.bookingservice.entity.ServiceDurationPrice;
import com.startocare.bookingservice.repository.ServiceDurationPriceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.List;

@Configuration
@Profile("test")
class TestDataInitializer {

    @Bean
    CommandLineRunner seedPricing(ServiceDurationPriceRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                repository.saveAll(List.of(
                        pricing(30, new BigDecimal("69.00")),
                        pricing(60, new BigDecimal("89.00")),
                        pricing(120, new BigDecimal("169.00")),
                        pricing(180, new BigDecimal("240.00")),
                        pricing(240, new BigDecimal("399.00")),
                        pricing(300, new BigDecimal("400.00"))
                ));
            }
        };
    }

    private ServiceDurationPrice pricing(int durationMinutes, BigDecimal serviceAmount) {
        ServiceDurationPrice price = new ServiceDurationPrice();
        price.setDurationMinutes(durationMinutes);
        price.setServiceAmount(serviceAmount);
        price.setActive(true);
        return price;
    }
}
