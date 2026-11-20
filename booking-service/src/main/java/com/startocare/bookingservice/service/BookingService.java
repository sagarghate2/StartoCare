package com.startocare.bookingservice.service;

import com.startocare.bookingservice.dto.request.BookingStatusUpdateRequest;
import com.startocare.bookingservice.dto.request.CompanionBookingResponseRequest;
import com.startocare.bookingservice.dto.request.CreateBookingRequest;
import com.startocare.bookingservice.dto.request.EstimateRequest;
import com.startocare.bookingservice.dto.request.TravelQuotationRequest;
import com.startocare.bookingservice.dto.response.BookingEstimateResponse;
import com.startocare.bookingservice.dto.response.BookingResponse;
import com.startocare.bookingservice.dto.response.ServiceDurationPriceResponse;
import com.startocare.bookingservice.dto.response.TravelLegQuoteResponse;
import com.startocare.bookingservice.entity.Booking;
import com.startocare.bookingservice.entity.BookingPriceBreakdown;
import com.startocare.bookingservice.entity.BookingTravelLeg;
import com.startocare.bookingservice.entity.ServiceDurationPrice;
import com.startocare.bookingservice.enums.BookingStatus;
import com.startocare.bookingservice.enums.CompanionBookingDecision;
import com.startocare.bookingservice.enums.Role;
import com.startocare.bookingservice.enums.TravelPlan;
import com.startocare.bookingservice.enums.TravelQuoteStatus;
import com.startocare.bookingservice.exception.BookingNotFoundException;
import com.startocare.bookingservice.exception.ForbiddenBookingAccessException;
import com.startocare.bookingservice.exception.InvalidBookingRequestException;
import com.startocare.bookingservice.exception.InvalidBookingStateException;
import com.startocare.bookingservice.mapper.BookingMapper;
import com.startocare.bookingservice.repository.BookingPriceBreakdownRepository;
import com.startocare.bookingservice.repository.BookingRepository;
import com.startocare.bookingservice.repository.ServiceDurationPriceRepository;
import com.startocare.bookingservice.security.BookingPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ServiceDurationPriceRepository serviceDurationPriceRepository;
    private final BookingPriceBreakdownRepository bookingPriceBreakdownRepository;
    private final TravelPricingService travelPricingService;
    private final BookingEventOutboxService bookingEventOutboxService;

    public BookingService(
            BookingRepository bookingRepository,
            ServiceDurationPriceRepository serviceDurationPriceRepository,
            BookingPriceBreakdownRepository bookingPriceBreakdownRepository,
            TravelPricingService travelPricingService,
            BookingEventOutboxService bookingEventOutboxService
    ) {
        this.bookingRepository = bookingRepository;
        this.serviceDurationPriceRepository = serviceDurationPriceRepository;
        this.bookingPriceBreakdownRepository = bookingPriceBreakdownRepository;
        this.travelPricingService = travelPricingService;
        this.bookingEventOutboxService = bookingEventOutboxService;
    }

    public List<ServiceDurationPriceResponse> getAvailableDurations() {
        return serviceDurationPriceRepository.findByActiveTrueOrderByDurationMinutesAsc().stream()
                .map(BookingMapper::toResponse)
                .toList();
    }

    public BookingEstimateResponse estimateBooking(EstimateRequest request) {
        validateEstimateRequest(request);
        ServiceDurationPrice pricing = resolvePrice(request.durationMinutes());
        TravelPricingService.TravelQuote quote = travelPricingService.calculate(
                request.companionId(),
                request.travelPlan(),
                request.patientHomeAddress(),
                request.hospitalAddress(),
                pricing.getServiceAmount()
        );

        return new BookingEstimateResponse(
                request.companionId(),
                request.durationMinutes(),
                request.travelPlan(),
                pricing.getServiceAmount(),
                quote.travelAmount(),
                quote.totalAmount(),
                TravelQuoteStatus.QUOTED,
                quote.legs()
        );
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request, BookingPrincipal principal) {
        if (principal.role() != Role.FAMILY) {
            throw new ForbiddenBookingAccessException("Only FAMILY users can create bookings");
        }

        validateCreateBookingRequest(request);
        BookingEstimateResponse estimate = estimateBooking(new EstimateRequest(
                request.companionId(),
                request.durationMinutes(),
                request.travelPlan(),
                request.hospitalName(),
                request.hospitalAddress(),
                request.patientName(),
                request.appointmentDate(),
                request.appointmentTime(),
                request.patientHomeAddress()
        ));

        Booking booking = new Booking();
        booking.setBookingReference(generateBookingReference());
        booking.setFamilyUserId(principal.userId());
        booking.setCompanionId(request.companionId());
        booking.setDurationMinutes(request.durationMinutes());
        booking.setTravelPlan(request.travelPlan());
        booking.setPatientHomeAddress(normalizeOptional(request.patientHomeAddress()));
        booking.setHospitalName(normalize(request.hospitalName()));
        booking.setHospitalAddress(normalize(request.hospitalAddress()));
        booking.setAppointmentDate(request.appointmentDate());
        booking.setAppointmentTime(request.appointmentTime());
        booking.setPatientName(normalize(request.patientName()));
        booking.setPatientAge(request.patientAge());
        booking.setPatientContactNumber(normalizeOptional(request.patientContactNumber()));
        booking.setServiceAmount(estimate.serviceAmount());
        booking.setTravelAmount(estimate.travelAmount());
        booking.setTotalAmount(estimate.totalAmount());
        booking.setTravelQuoteStatus(estimate.travelQuoteStatus());
        booking.setBookingStatus(BookingStatus.AWAITING_PAYMENT);
        for (int index = 0; index < estimate.travelLegs().size(); index++) {
            booking.addTravelLeg(toEntity(estimate.travelLegs().get(index), index + 1));
        }

        booking = bookingRepository.save(booking);
        BookingPriceBreakdown breakdown = new BookingPriceBreakdown();
        breakdown.setBooking(booking);
        breakdown.setServiceAmount(booking.getServiceAmount());
        breakdown.setTravelAmount(booking.getTravelAmount());
        breakdown.setTotalAmount(booking.getTotalAmount());
        breakdown.setCurrency("INR");
        bookingPriceBreakdownRepository.save(breakdown);
        bookingEventOutboxService.queuePaymentRequest(booking);
        return BookingMapper.toResponse(booking);
    }

    public BookingResponse getBooking(UUID bookingId, BookingPrincipal principal) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));

        boolean familyOwnsBooking = principal.role() == Role.FAMILY
                && booking.getFamilyUserId().equals(principal.userId());
        boolean companionOwnsBooking = principal.role() == Role.COMPANION
                && Objects.equals(booking.getCompanionId(), principal.userId());
        if (principal.role() != Role.ADMIN && !familyOwnsBooking && !companionOwnsBooking) {
            throw new ForbiddenBookingAccessException("You cannot access another user's booking");
        }

        return BookingMapper.toResponse(booking);
    }

    public Page<BookingResponse> getMyBookings(BookingPrincipal principal, Pageable pageable) {
        if (principal.role() != Role.FAMILY && principal.role() != Role.ADMIN) {
            throw new ForbiddenBookingAccessException("Only family or admin users may list bookings");
        }

        Page<Booking> page = principal.role() == Role.ADMIN
                ? bookingRepository.findAll(pageable)
                : bookingRepository.findByFamilyUserIdOrderByCreatedAtDesc(principal.userId(), pageable);

        return page.map(BookingMapper::toResponse);
    }

    public Page<BookingResponse> getCompanionRequests(BookingPrincipal principal, Pageable pageable) {
        if (principal.role() != Role.COMPANION) {
            throw new ForbiddenBookingAccessException("Only companions may list their booking requests");
        }
        return bookingRepository.findByCompanionIdAndBookingStatusOrderByCreatedAtDesc(
                        principal.userId(),
                        BookingStatus.PENDING,
                        pageable
                )
                .map(BookingMapper::toResponse);
    }

    @Transactional
    public BookingResponse respondToBooking(
            UUID bookingId,
            CompanionBookingResponseRequest request,
            BookingPrincipal principal
    ) {
        if (principal.role() != Role.COMPANION) {
            throw new ForbiddenBookingAccessException("Only the selected companion may respond to a booking");
        }

        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));
        if (!principal.userId().equals(booking.getCompanionId())) {
            throw new ForbiddenBookingAccessException("You are not the companion selected for this booking");
        }
        if (booking.getBookingStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException("Only pending booking requests can be accepted or declined");
        }

        if (request.decision() == CompanionBookingDecision.ACCEPTED) {
            if (booking.getTotalAmount() == null) {
                throw new InvalidBookingStateException("Booking cannot be accepted before its total is calculated");
            }
            booking.setBookingStatus(BookingStatus.CONFIRMED);
            booking.setTravelQuoteStatus(TravelQuoteStatus.CONFIRMED);
        } else {
            booking.setBookingStatus(BookingStatus.DECLINED);
        }

        return BookingMapper.toResponse(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse cancelBooking(UUID bookingId, BookingPrincipal principal) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));

        if (principal.role() != Role.ADMIN && !booking.getFamilyUserId().equals(principal.userId())) {
            throw new ForbiddenBookingAccessException("You cannot cancel another user's booking");
        }

        if (!canCancel(booking.getBookingStatus())) {
            throw new InvalidBookingStateException("This booking cannot be cancelled in its current state");
        }

        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        return BookingMapper.toResponse(booking);
    }

    @Transactional
    public BookingResponse updateStatus(UUID bookingId, BookingStatusUpdateRequest request, BookingPrincipal principal) {
        if (principal.role() != Role.ADMIN) {
            throw new ForbiddenBookingAccessException("Only admin users may update booking status");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));

        BookingStatus targetStatus = request.status();
        validateStatusTransition(booking.getBookingStatus(), targetStatus);

        if (targetStatus == BookingStatus.CONFIRMED && booking.getTotalAmount() == null) {
            throw new InvalidBookingStateException("Booking cannot be confirmed before the travel quotation is approved");
        }

        booking.setBookingStatus(targetStatus);
        if (targetStatus == BookingStatus.CONFIRMED
                && booking.getTravelQuoteStatus() != TravelQuoteStatus.CONFIRMED) {
            booking.setTravelQuoteStatus(TravelQuoteStatus.CONFIRMED);
        }

        bookingRepository.save(booking);
        return BookingMapper.toResponse(booking);
    }

    @Transactional
    public BookingResponse updateTravelQuotation(UUID bookingId, TravelQuotationRequest request, BookingPrincipal principal) {
        if (principal.role() != Role.ADMIN) {
            throw new ForbiddenBookingAccessException("Only admin users may manage travel quotations");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));

        if (request.travelAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidBookingRequestException("Travel amount cannot be negative");
        }

        if (booking.getBookingStatus() == BookingStatus.CANCELLED || booking.getBookingStatus() == BookingStatus.COMPLETED) {
            throw new InvalidBookingStateException("Travel quotation cannot be changed after cancellation or completion");
        }

        booking.setTravelAmount(request.travelAmount());
        booking.setTravelQuoteStatus(request.travelQuoteStatus());
        booking.setTotalAmount(booking.getServiceAmount().add(request.travelAmount()));
        booking.getTravelLegs().clear();
        bookingRepository.save(booking);

        BookingPriceBreakdown breakdown = new BookingPriceBreakdown();
        breakdown.setBooking(booking);
        breakdown.setServiceAmount(booking.getServiceAmount());
        breakdown.setTravelAmount(request.travelAmount());
        breakdown.setTotalAmount(booking.getTotalAmount());
        breakdown.setCurrency("INR");
        bookingPriceBreakdownRepository.save(breakdown);

        return BookingMapper.toResponse(booking);
    }

    private ServiceDurationPrice resolvePrice(Integer durationMinutes) {
        return serviceDurationPriceRepository.findByDurationMinutesAndActiveTrue(durationMinutes)
                .orElseThrow(() -> new InvalidBookingRequestException("Invalid or inactive duration selected"));
    }

    private void validateEstimateRequest(EstimateRequest request) {
        if (request == null) {
            throw new InvalidBookingRequestException("Request body is required");
        }

        if (request.durationMinutes() == null || request.durationMinutes() <= 0) {
            throw new InvalidBookingRequestException("Duration must be a positive number of minutes");
        }
        if (request.companionId() == null) {
            throw new InvalidBookingRequestException("A verified companion must be selected");
        }

        validateCommonBookingFields(request.travelPlan(), request.hospitalName(), request.hospitalAddress(),
                request.patientName(), request.appointmentDate(), request.appointmentTime(), request.patientHomeAddress());
        resolvePrice(request.durationMinutes());
    }

    private void validateCreateBookingRequest(CreateBookingRequest request) {
        if (request == null) {
            throw new InvalidBookingRequestException("Request body is required");
        }

        if (request.durationMinutes() == null || request.durationMinutes() <= 0) {
            throw new InvalidBookingRequestException("Duration must be a positive number of minutes");
        }
        if (request.companionId() == null) {
            throw new InvalidBookingRequestException("A verified companion must be selected");
        }

        validateCommonBookingFields(request.travelPlan(), request.hospitalName(), request.hospitalAddress(),
                request.patientName(), request.appointmentDate(), request.appointmentTime(), request.patientHomeAddress());
        if (request.patientAge() != null && request.patientAge() <= 0) {
            throw new InvalidBookingRequestException("Patient age must be positive when provided");
        }
    }

    private void validateCommonBookingFields(
            TravelPlan travelPlan,
            String hospitalName,
            String hospitalAddress,
            String patientName,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String patientHomeAddress
    ) {
        if (travelPlan == null) {
            throw new InvalidBookingRequestException("Travel plan is required");
        }
        if (hospitalName == null || normalize(hospitalName).isBlank()) {
            throw new InvalidBookingRequestException("Hospital name is required");
        }
        if (hospitalAddress == null || normalize(hospitalAddress).isBlank()) {
            throw new InvalidBookingRequestException("Hospital address is required");
        }
        if (patientName == null || normalize(patientName).isBlank()) {
            throw new InvalidBookingRequestException("Patient name is required");
        }
        if (appointmentDate == null) {
            throw new InvalidBookingRequestException("Appointment date is required");
        }
        if (appointmentTime == null) {
            throw new InvalidBookingRequestException("Appointment time is required");
        }
        if (appointmentDate.isBefore(LocalDate.now())) {
            throw new InvalidBookingRequestException("Appointment date cannot be in the past");
        }

        if (travelPlan == TravelPlan.PLAN_1 || travelPlan == TravelPlan.PLAN_3) {
            if (patientHomeAddress == null || normalize(patientHomeAddress).isBlank()) {
                throw new InvalidBookingRequestException("Patient home address is required for travel plans 1 and 3");
            }
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null ? null : value.trim();
    }

    private String generateBookingReference() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "BK-" + suffix;
    }

    private BookingTravelLeg toEntity(TravelLegQuoteResponse quote, int legOrder) {
        BookingTravelLeg leg = new BookingTravelLeg();
        leg.setLegOrder(legOrder);
        leg.setLegType(quote.leg());
        leg.setOriginLabel(quote.from());
        leg.setDestinationLabel(quote.to());
        leg.setDistanceMeters(quote.distanceMeters());
        leg.setDistanceKm(quote.distanceKm());
        leg.setBaseFare(quote.baseFare());
        leg.setDistanceCharge(quote.distanceCharge());
        leg.setTotalFare(quote.totalFare());
        return leg;
    }

    private boolean canCancel(BookingStatus status) {
        return status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED || status == BookingStatus.COMPANION_ASSIGNED;
    }

    private void validateStatusTransition(BookingStatus currentStatus, BookingStatus newStatus) {
        if (currentStatus == newStatus) {
            return;
        }

        boolean valid = switch (currentStatus) {
                case PENDING -> newStatus == BookingStatus.CANCELLED;
                case CONFIRMED -> newStatus == BookingStatus.COMPANION_ASSIGNED || newStatus == BookingStatus.CANCELLED;
                case COMPANION_ASSIGNED -> newStatus == BookingStatus.IN_PROGRESS || newStatus == BookingStatus.CANCELLED;
                case IN_PROGRESS -> newStatus == BookingStatus.COMPLETED || newStatus == BookingStatus.CANCELLED;
                default -> false;
        };

        if (!valid) {
            throw new InvalidBookingStateException(
                    "Invalid booking state transition from " + currentStatus + " to " + newStatus
            );
        }
    }
}
