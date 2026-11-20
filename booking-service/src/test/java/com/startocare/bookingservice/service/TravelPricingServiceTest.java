package com.startocare.bookingservice.service;

import com.startocare.bookingservice.dto.response.CompanionLocationResponse;
import com.startocare.bookingservice.dto.response.TravelLegQuoteResponse;
import com.startocare.bookingservice.entity.TravelPricingConfig;
import com.startocare.bookingservice.enums.TravelPlan;
import com.startocare.bookingservice.exception.EstimateUnavailableException;
import com.startocare.bookingservice.repository.TravelPricingConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravelPricingServiceTest {

    private static final UUID COMPANION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final CompanionLocationResponse COMPANION =
            new CompanionLocationResponse(COMPANION_ID, "Companion base", 17.4, 78.5);

    @Mock
    private TravelPricingConfigRepository configRepository;

    @Mock
    private CompanionLocationClient companionLocationClient;

    @Mock
    private GeoapifyRoutingClient geoapifyRoutingClient;

    private TravelPricingService travelPricingService;

    @BeforeEach
    void setUp() {
        travelPricingService = new TravelPricingService(
                configRepository,
                companionLocationClient,
                geoapifyRoutingClient
        );
    }

    @Test
    void planOnePricesCompanionToPatientHomeAndReturnAtExactDistance() {
        configureRates();
        when(companionLocationClient.getVerifiedLocation(COMPANION_ID)).thenReturn(COMPANION);
        when(geoapifyRoutingClient.getTwoWheelerDistanceMeters(any(), any(), any()))
                .thenReturn(1234L, 2000L);

        TravelPricingService.TravelQuote quote = travelPricingService.calculate(
                COMPANION_ID,
                TravelPlan.PLAN_1,
                "Nagole, Hyderabad",
                "Apollo Hospital, Hyderabad",
                BigDecimal.valueOf(89)
        );

        assertThat(quote.travelAmount()).isEqualByComparingTo("65.87");
        assertThat(quote.totalAmount()).isEqualByComparingTo("154.87");
        assertThat(quote.legs()).extracting(TravelLegQuoteResponse::distanceKm).containsExactly(
                new BigDecimal("1.234"),
                new BigDecimal("2.000")
        );
        assertThat(quote.legs()).extracting(TravelLegQuoteResponse::totalFare).containsExactly(
                new BigDecimal("29.87"),
                new BigDecimal("36.00")
        );

        ArgumentCaptor<GeoapifyRoutingClient.RoutePoint> origins =
                ArgumentCaptor.forClass(GeoapifyRoutingClient.RoutePoint.class);
        ArgumentCaptor<GeoapifyRoutingClient.RoutePoint> destinations =
                ArgumentCaptor.forClass(GeoapifyRoutingClient.RoutePoint.class);
        verify(geoapifyRoutingClient, times(2))
                .getTwoWheelerDistanceMeters(eq(COMPANION), origins.capture(), destinations.capture());
        assertThat(origins.getAllValues()).containsExactly(
                GeoapifyRoutingClient.RoutePoint.companion(),
                GeoapifyRoutingClient.RoutePoint.address("Nagole, Hyderabad")
        );
        assertThat(destinations.getAllValues()).containsExactly(
                GeoapifyRoutingClient.RoutePoint.address("Nagole, Hyderabad"),
                GeoapifyRoutingClient.RoutePoint.companion()
        );
    }

    @Test
    void planTwoPricesCompanionHospitalRoundTrip() {
        configureRates();
        when(companionLocationClient.getVerifiedLocation(COMPANION_ID)).thenReturn(COMPANION);
        when(geoapifyRoutingClient.getTwoWheelerDistanceMeters(any(), any(), any()))
                .thenReturn(3000L, 4000L);

        TravelPricingService.TravelQuote quote = travelPricingService.calculate(
                COMPANION_ID,
                TravelPlan.PLAN_2,
                null,
                "Hospital, Hyderabad",
                BigDecimal.valueOf(69)
        );

        assertThat(quote.travelAmount()).isEqualByComparingTo("96.00");
        assertThat(quote.totalAmount()).isEqualByComparingTo("165.00");
        assertThat(quote.legs()).extracting(TravelLegQuoteResponse::leg).containsExactly(
                "COMPANION_TO_HOSPITAL",
                "HOSPITAL_TO_COMPANION"
        );

        ArgumentCaptor<GeoapifyRoutingClient.RoutePoint> origins =
                ArgumentCaptor.forClass(GeoapifyRoutingClient.RoutePoint.class);
        ArgumentCaptor<GeoapifyRoutingClient.RoutePoint> destinations =
                ArgumentCaptor.forClass(GeoapifyRoutingClient.RoutePoint.class);
        verify(geoapifyRoutingClient, times(2))
                .getTwoWheelerDistanceMeters(eq(COMPANION), origins.capture(), destinations.capture());
        assertThat(origins.getAllValues()).containsExactly(
                GeoapifyRoutingClient.RoutePoint.companion(),
                GeoapifyRoutingClient.RoutePoint.address("Hospital, Hyderabad")
        );
        assertThat(destinations.getAllValues()).containsExactly(
                GeoapifyRoutingClient.RoutePoint.address("Hospital, Hyderabad"),
                GeoapifyRoutingClient.RoutePoint.companion()
        );
    }

    @Test
    void planThreePricesCompanionToHomeAndHospitalBackToCompanion() {
        configureRates();
        when(companionLocationClient.getVerifiedLocation(COMPANION_ID)).thenReturn(COMPANION);
        when(geoapifyRoutingClient.getTwoWheelerDistanceMeters(any(), any(), any()))
                .thenReturn(3000L, 4000L);

        TravelPricingService.TravelQuote quote = travelPricingService.calculate(
                COMPANION_ID,
                TravelPlan.PLAN_3,
                "Nagole, Hyderabad",
                "Apollo Hospital, Hyderabad",
                BigDecimal.valueOf(89)
        );

        assertThat(quote.legs()).extracting(TravelLegQuoteResponse::leg).containsExactly(
                "COMPANION_TO_PATIENT_HOME",
                "HOSPITAL_TO_COMPANION"
        );
        ArgumentCaptor<GeoapifyRoutingClient.RoutePoint> origins =
                ArgumentCaptor.forClass(GeoapifyRoutingClient.RoutePoint.class);
        ArgumentCaptor<GeoapifyRoutingClient.RoutePoint> destinations =
                ArgumentCaptor.forClass(GeoapifyRoutingClient.RoutePoint.class);
        verify(geoapifyRoutingClient, times(2))
                .getTwoWheelerDistanceMeters(eq(COMPANION), origins.capture(), destinations.capture());
        assertThat(origins.getAllValues()).containsExactly(
                GeoapifyRoutingClient.RoutePoint.companion(),
                GeoapifyRoutingClient.RoutePoint.address("Apollo Hospital, Hyderabad")
        );
        assertThat(destinations.getAllValues()).containsExactly(
                GeoapifyRoutingClient.RoutePoint.address("Nagole, Hyderabad"),
                GeoapifyRoutingClient.RoutePoint.companion()
        );
    }

    @Test
    void estimateIsUnavailableUntilRatesAreConfigured() {
        when(configRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> travelPricingService.calculate(
                COMPANION_ID,
                TravelPlan.PLAN_1,
                "Nagole, Hyderabad",
                "Hospital, Hyderabad",
                BigDecimal.valueOf(69)
        )).isInstanceOf(EstimateUnavailableException.class)
                .hasMessage("Travel fares have not been configured");
    }

    private void configureRates() {
        TravelPricingConfig config = new TravelPricingConfig(1);
        config.setBaseFarePerLeg(BigDecimal.valueOf(20));
        config.setRatePerKm(BigDecimal.valueOf(8));
        when(configRepository.findById(1)).thenReturn(Optional.of(config));
    }
}
