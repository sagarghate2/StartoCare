package com.startocare.bookingservice.controller;

import com.startocare.bookingservice.dto.request.TravelPricingConfigRequest;
import com.startocare.bookingservice.dto.response.TravelPricingConfigResponse;
import com.startocare.bookingservice.enums.Role;
import com.startocare.bookingservice.security.BookingPrincipal;
import com.startocare.bookingservice.service.TravelPricingService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/travel-pricing")
public class AdminTravelPricingController {

    private final TravelPricingService travelPricingService;

    public AdminTravelPricingController(TravelPricingService travelPricingService) {
        this.travelPricingService = travelPricingService;
    }

    @GetMapping
    public TravelPricingConfigResponse getConfiguration(
            @AuthenticationPrincipal BookingPrincipal principal
    ) {
        requireAdmin(principal);
        return travelPricingService.getConfiguration();
    }

    @PutMapping
    public TravelPricingConfigResponse updateConfiguration(
            @AuthenticationPrincipal BookingPrincipal principal,
            @Valid @RequestBody TravelPricingConfigRequest request
    ) {
        requireAdmin(principal);
        return travelPricingService.updateConfiguration(request);
    }

    private void requireAdmin(BookingPrincipal principal) {
        if (principal.role() != Role.ADMIN) {
            throw new AccessDeniedException("Only ADMIN users may configure travel pricing");
        }
    }
}
