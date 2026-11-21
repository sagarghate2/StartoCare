package com.startocare.bookingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "travel_pricing_config")
public class TravelPricingConfig {

    @Id
    private Integer id;

    @Column(name = "base_fare_per_leg", precision = 10, scale = 2)
    private BigDecimal baseFarePerLeg;

    @Column(name = "rate_per_km", precision = 10, scale = 2)
    private BigDecimal ratePerKm;

    public TravelPricingConfig() {
    }

    public TravelPricingConfig(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public BigDecimal getBaseFarePerLeg() {
        return baseFarePerLeg;
    }

    public void setBaseFarePerLeg(BigDecimal baseFarePerLeg) {
        this.baseFarePerLeg = baseFarePerLeg;
    }

    public BigDecimal getRatePerKm() {
        return ratePerKm;
    }

    public void setRatePerKm(BigDecimal ratePerKm) {
        this.ratePerKm = ratePerKm;
    }
}
