package com.startocare.bookingservice.service;

import com.startocare.bookingservice.dto.request.TravelPricingConfigRequest;
import com.startocare.bookingservice.dto.response.CompanionLocationResponse;
import com.startocare.bookingservice.dto.response.TravelLegQuoteResponse;
import com.startocare.bookingservice.dto.response.TravelPricingConfigResponse;
import com.startocare.bookingservice.entity.TravelPricingConfig;
import com.startocare.bookingservice.enums.TravelPlan;
import com.startocare.bookingservice.exception.EstimateUnavailableException;
import com.startocare.bookingservice.repository.TravelPricingConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TravelPricingService {

    private static final BigDecimal METERS_PER_KILOMETER = BigDecimal.valueOf(1000);
    private static final int PRICING_CONFIG_ID = 1;

    private final TravelPricingConfigRepository configRepository;
    private final CompanionLocationClient companionLocationClient;
    private final GeoapifyRoutingClient geoapifyRoutingClient;

    public TravelPricingService(
            TravelPricingConfigRepository configRepository,
            CompanionLocationClient companionLocationClient,
            GeoapifyRoutingClient geoapifyRoutingClient
    ) {
        this.configRepository = configRepository;
        this.companionLocationClient = companionLocationClient;
        this.geoapifyRoutingClient = geoapifyRoutingClient;
    }

    @Transactional(readOnly = true)
    public TravelPricingConfigResponse getConfiguration() {
        return configRepository.findById(PRICING_CONFIG_ID)
                .map(this::toResponse)
                .orElseGet(() -> new TravelPricingConfigResponse(false, null, null, "INR"));
    }

    @Transactional
    public TravelPricingConfigResponse updateConfiguration(TravelPricingConfigRequest request) {
        TravelPricingConfig config = configRepository.findById(PRICING_CONFIG_ID)
                .orElseGet(() -> new TravelPricingConfig(PRICING_CONFIG_ID));
        config.setBaseFarePerLeg(request.baseFarePerLeg());
        config.setRatePerKm(request.ratePerKm());
        return toResponse(configRepository.save(config));
    }

    @Transactional(readOnly = true)
    public TravelQuote calculate(
            UUID companionId,
            TravelPlan travelPlan,
            String patientHomeAddress,
            String hospitalAddress,
            BigDecimal serviceAmount
    ) {
        TravelPricingConfig config = configRepository.findById(PRICING_CONFIG_ID)
                .filter(candidate -> candidate.getBaseFarePerLeg() != null && candidate.getRatePerKm() != null)
                .orElseThrow(() -> new EstimateUnavailableException("Travel fares have not been configured"));
        CompanionLocationResponse companion = companionLocationClient.getVerifiedLocation(companionId);

        BigDecimal baseFare = config.getBaseFarePerLeg();
        BigDecimal ratePerKm = config.getRatePerKm();
        List<LegSpec> legs = legsFor(travelPlan, patientHomeAddress, hospitalAddress);
        List<TravelLegQuoteResponse> quotes = new ArrayList<>(legs.size());
        BigDecimal travelAmount = BigDecimal.ZERO;

        for (LegSpec leg : legs) {
            long distanceMeters = geoapifyRoutingClient.getTwoWheelerDistanceMeters(
                    companion,
                    leg.origin(),
                    leg.destination()
            );
            BigDecimal distanceKm = BigDecimal.valueOf(distanceMeters)
                    .divide(METERS_PER_KILOMETER, 3, RoundingMode.UNNECESSARY);
            BigDecimal distanceCharge = ratePerKm.multiply(BigDecimal.valueOf(distanceMeters))
                    .divide(METERS_PER_KILOMETER, 2, RoundingMode.HALF_UP);
            BigDecimal totalFare = baseFare.add(distanceCharge).setScale(2, RoundingMode.HALF_UP);
            quotes.add(new TravelLegQuoteResponse(
                    leg.type(),
                    leg.originLabel(),
                    leg.destinationLabel(),
                    distanceMeters,
                    distanceKm,
                    baseFare,
                    distanceCharge,
                    totalFare
            ));
            travelAmount = travelAmount.add(totalFare);
        }

        travelAmount = travelAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = serviceAmount.add(travelAmount).setScale(2, RoundingMode.HALF_UP);
        return new TravelQuote(travelAmount, totalAmount, List.copyOf(quotes));
    }

    private List<LegSpec> legsFor(TravelPlan plan, String patientHomeAddress, String hospitalAddress) {
        GeoapifyRoutingClient.RoutePoint companion = GeoapifyRoutingClient.RoutePoint.companion();
        GeoapifyRoutingClient.RoutePoint hospital = GeoapifyRoutingClient.RoutePoint.address(hospitalAddress);
        return switch (plan) {
            case PLAN_1 -> {
                GeoapifyRoutingClient.RoutePoint home = GeoapifyRoutingClient.RoutePoint.address(patientHomeAddress);
                yield List.of(
                        new LegSpec("COMPANION_TO_PATIENT_HOME", "Companion base", "Patient home", companion, home),
                        new LegSpec("PATIENT_HOME_TO_COMPANION", "Patient home", "Companion base", home, companion)
                );
            }
            case PLAN_2 -> List.of(
                    new LegSpec("COMPANION_TO_HOSPITAL", "Companion base", "Hospital", companion, hospital),
                    new LegSpec("HOSPITAL_TO_COMPANION", "Hospital", "Companion base", hospital, companion)
            );
            case PLAN_3 -> {
                GeoapifyRoutingClient.RoutePoint home = GeoapifyRoutingClient.RoutePoint.address(patientHomeAddress);
                yield List.of(
                        new LegSpec("COMPANION_TO_PATIENT_HOME", "Companion base", "Patient home", companion, home),
                        new LegSpec("HOSPITAL_TO_COMPANION", "Hospital", "Companion base", hospital, companion)
                );
            }
        };
    }

    private TravelPricingConfigResponse toResponse(TravelPricingConfig config) {
        boolean configured = config.getBaseFarePerLeg() != null && config.getRatePerKm() != null;
        return new TravelPricingConfigResponse(
                configured,
                config.getBaseFarePerLeg(),
                config.getRatePerKm(),
                "INR"
        );
    }

    private record LegSpec(
            String type,
            String originLabel,
            String destinationLabel,
            GeoapifyRoutingClient.RoutePoint origin,
            GeoapifyRoutingClient.RoutePoint destination
    ) {
    }

    public record TravelQuote(
            BigDecimal travelAmount,
            BigDecimal totalAmount,
            List<TravelLegQuoteResponse> legs
    ) {
    }
}
