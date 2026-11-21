package com.startocare.bookingservice.mapper;

import com.startocare.bookingservice.dto.response.BookingEstimateResponse;
import com.startocare.bookingservice.dto.response.BookingResponse;
import com.startocare.bookingservice.dto.response.ServiceDurationPriceResponse;
import com.startocare.bookingservice.dto.response.TravelLegQuoteResponse;
import com.startocare.bookingservice.entity.Booking;
import com.startocare.bookingservice.entity.BookingTravelLeg;
import com.startocare.bookingservice.entity.ServiceDurationPrice;

public final class BookingMapper {

    private BookingMapper() {
    }

    public static BookingEstimateResponse toEstimateResponse(Booking booking) {
        return new BookingEstimateResponse(
                booking.getCompanionId(),
                booking.getDurationMinutes(),
                booking.getTravelPlan(),
                booking.getServiceAmount(),
                booking.getTravelAmount(),
                booking.getTotalAmount(),
                booking.getTravelQuoteStatus(),
                booking.getTravelLegs().stream().map(BookingMapper::toResponse).toList()
        );
    }

    public static BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getBookingReference(),
                booking.getFamilyUserId(),
                booking.getCompanionId(),
                booking.getDurationMinutes(),
                booking.getTravelPlan(),
                booking.getPatientName(),
                booking.getHospitalName(),
                booking.getHospitalAddress(),
                booking.getPatientHomeAddress(),
                booking.getAppointmentDate(),
                booking.getAppointmentTime(),
                booking.getServiceAmount(),
                booking.getTravelAmount(),
                booking.getTotalAmount(),
                booking.getTravelQuoteStatus(),
                booking.getBookingStatus(),
                booking.getTravelLegs().stream().map(BookingMapper::toResponse).toList(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }

    public static ServiceDurationPriceResponse toResponse(ServiceDurationPrice price) {
        return new ServiceDurationPriceResponse(
                price.getDurationMinutes(),
                price.getServiceAmount(),
                price.getActive()
        );
    }

    public static TravelLegQuoteResponse toResponse(BookingTravelLeg leg) {
        return new TravelLegQuoteResponse(
                leg.getLegType(),
                leg.getOriginLabel(),
                leg.getDestinationLabel(),
                leg.getDistanceMeters(),
                leg.getDistanceKm(),
                leg.getBaseFare(),
                leg.getDistanceCharge(),
                leg.getTotalFare()
        );
    }
}
