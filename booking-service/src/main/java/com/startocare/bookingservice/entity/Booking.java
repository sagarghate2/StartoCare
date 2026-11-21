package com.startocare.bookingservice.entity;

import com.startocare.bookingservice.enums.BookingStatus;
import com.startocare.bookingservice.enums.TravelPlan;
import com.startocare.bookingservice.enums.TravelQuoteStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "bookings",
        indexes = {
                @Index(name = "idx_bookings_family_user_id", columnList = "family_user_id"),
                @Index(name = "idx_bookings_status", columnList = "booking_status"),
                @Index(name = "idx_bookings_appointment_date", columnList = "appointment_date")
        }
)
public class Booking {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 32)
    private String bookingReference;

    @Column(name = "family_user_id", nullable = false)
    private UUID familyUserId;

    @Column(name = "companion_id")
    private UUID companionId;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_plan", nullable = false, length = 20)
    private TravelPlan travelPlan;

    @Column(name = "patient_home_address", columnDefinition = "TEXT")
    private String patientHomeAddress;

    @Column(name = "hospital_name", nullable = false, length = 200)
    private String hospitalName;

    @Column(name = "hospital_address", nullable = false, columnDefinition = "TEXT")
    private String hospitalAddress;

    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;

    @Column(name = "appointment_time", nullable = false)
    private LocalTime appointmentTime;

    @Column(name = "patient_name", nullable = false, length = 120)
    private String patientName;

    @Column(name = "patient_age")
    private Integer patientAge;

    @Column(name = "patient_contact_number", length = 20)
    private String patientContactNumber;

    @Column(name = "service_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal serviceAmount;

    @Column(name = "travel_amount", precision = 10, scale = 2)
    private BigDecimal travelAmount;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_quote_status", nullable = false, length = 20)
    private TravelQuoteStatus travelQuoteStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false, length = 20)
    private BookingStatus bookingStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BookingPriceBreakdown> priceBreakdowns = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<BookingTravelLeg> travelLegs = new ArrayList<>();

    public Booking() {
    }

    public UUID getId() {
        return id;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public UUID getFamilyUserId() {
        return familyUserId;
    }

    public void setFamilyUserId(UUID familyUserId) {
        this.familyUserId = familyUserId;
    }

    public UUID getCompanionId() {
        return companionId;
    }

    public void setCompanionId(UUID companionId) {
        this.companionId = companionId;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public TravelPlan getTravelPlan() {
        return travelPlan;
    }

    public void setTravelPlan(TravelPlan travelPlan) {
        this.travelPlan = travelPlan;
    }

    public String getPatientHomeAddress() {
        return patientHomeAddress;
    }

    public void setPatientHomeAddress(String patientHomeAddress) {
        this.patientHomeAddress = patientHomeAddress;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public String getHospitalAddress() {
        return hospitalAddress;
    }

    public void setHospitalAddress(String hospitalAddress) {
        this.hospitalAddress = hospitalAddress;
    }

    public LocalDate getAppointmentDate() {
        return appointmentDate;
    }

    public void setAppointmentDate(LocalDate appointmentDate) {
        this.appointmentDate = appointmentDate;
    }

    public LocalTime getAppointmentTime() {
        return appointmentTime;
    }

    public void setAppointmentTime(LocalTime appointmentTime) {
        this.appointmentTime = appointmentTime;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public Integer getPatientAge() {
        return patientAge;
    }

    public void setPatientAge(Integer patientAge) {
        this.patientAge = patientAge;
    }

    public String getPatientContactNumber() {
        return patientContactNumber;
    }

    public void setPatientContactNumber(String patientContactNumber) {
        this.patientContactNumber = patientContactNumber;
    }

    public BigDecimal getServiceAmount() {
        return serviceAmount;
    }

    public void setServiceAmount(BigDecimal serviceAmount) {
        this.serviceAmount = serviceAmount;
    }

    public BigDecimal getTravelAmount() {
        return travelAmount;
    }

    public void setTravelAmount(BigDecimal travelAmount) {
        this.travelAmount = travelAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public TravelQuoteStatus getTravelQuoteStatus() {
        return travelQuoteStatus;
    }

    public void setTravelQuoteStatus(TravelQuoteStatus travelQuoteStatus) {
        this.travelQuoteStatus = travelQuoteStatus;
    }

    public BookingStatus getBookingStatus() {
        return bookingStatus;
    }

    public void setBookingStatus(BookingStatus bookingStatus) {
        this.bookingStatus = bookingStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<BookingPriceBreakdown> getPriceBreakdowns() {
        return priceBreakdowns;
    }

    public List<BookingTravelLeg> getTravelLegs() {
        return travelLegs;
    }

    public void addTravelLeg(BookingTravelLeg travelLeg) {
        travelLeg.setBooking(this);
        travelLegs.add(travelLeg);
    }

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}
