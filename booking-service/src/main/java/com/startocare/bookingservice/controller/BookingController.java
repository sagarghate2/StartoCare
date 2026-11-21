package com.startocare.bookingservice.controller;

import com.startocare.bookingservice.dto.request.BookingStatusUpdateRequest;
import com.startocare.bookingservice.dto.request.CompanionBookingResponseRequest;
import com.startocare.bookingservice.dto.request.CreateBookingRequest;
import com.startocare.bookingservice.dto.request.EstimateRequest;
import com.startocare.bookingservice.dto.request.TravelQuotationRequest;
import com.startocare.bookingservice.dto.response.BookingEstimateResponse;
import com.startocare.bookingservice.dto.response.BookingResponse;
import com.startocare.bookingservice.dto.response.ServiceDurationPriceResponse;
import com.startocare.bookingservice.enums.Role;
import com.startocare.bookingservice.exception.ForbiddenBookingAccessException;
import com.startocare.bookingservice.security.BookingPrincipal;
import com.startocare.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/pricing/durations")
    public ResponseEntity<List<ServiceDurationPriceResponse>> getDurationPricing() {
        return ResponseEntity.ok(bookingService.getAvailableDurations());
    }

    @PostMapping("/estimate")
    public ResponseEntity<BookingEstimateResponse> estimate(
            @AuthenticationPrincipal BookingPrincipal principal,
            @Valid @RequestBody EstimateRequest request
    ) {
        if (principal.role() != Role.FAMILY) {
            throw new ForbiddenBookingAccessException("Only FAMILY users may request booking estimates");
        }
        return ResponseEntity.ok(bookingService.estimateBooking(request));
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @AuthenticationPrincipal BookingPrincipal principal,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        return ResponseEntity.ok(bookingService.createBooking(request, principal));
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(
            @AuthenticationPrincipal BookingPrincipal principal,
            @PathVariable UUID bookingId
    ) {
        return ResponseEntity.ok(bookingService.getBooking(bookingId, principal));
    }

    @GetMapping("/my-bookings")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @AuthenticationPrincipal BookingPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(bookingService.getMyBookings(principal, pageable));
    }

    @GetMapping("/companion-requests")
    public ResponseEntity<Page<BookingResponse>> getCompanionRequests(
            @AuthenticationPrincipal BookingPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(bookingService.getCompanionRequests(principal, pageable));
    }

    @PatchMapping("/{bookingId}/companion-response")
    public ResponseEntity<BookingResponse> respondToBooking(
            @AuthenticationPrincipal BookingPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody CompanionBookingResponseRequest request
    ) {
        return ResponseEntity.ok(bookingService.respondToBooking(bookingId, request, principal));
    }

    @PatchMapping("/{bookingId}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            @AuthenticationPrincipal BookingPrincipal principal,
            @PathVariable UUID bookingId
    ) {
        return ResponseEntity.ok(bookingService.cancelBooking(bookingId, principal));
    }

    @PatchMapping("/{bookingId}/status")
    public ResponseEntity<BookingResponse> updateStatus(
            @AuthenticationPrincipal BookingPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody BookingStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(bookingService.updateStatus(bookingId, request, principal));
    }

    @PatchMapping("/{bookingId}/travel-quotation")
    public ResponseEntity<BookingResponse> updateTravelQuotation(
            @AuthenticationPrincipal BookingPrincipal principal,
            @PathVariable UUID bookingId,
            @Valid @RequestBody TravelQuotationRequest request
    ) {
        return ResponseEntity.ok(bookingService.updateTravelQuotation(bookingId, request, principal));
    }
}
