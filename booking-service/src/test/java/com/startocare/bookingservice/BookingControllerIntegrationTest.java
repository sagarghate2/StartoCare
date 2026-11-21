package com.startocare.bookingservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.bookingservice.dto.request.BookingStatusUpdateRequest;
import com.startocare.bookingservice.dto.request.CompanionBookingResponseRequest;
import com.startocare.bookingservice.dto.request.CreateBookingRequest;
import com.startocare.bookingservice.dto.request.EstimateRequest;
import com.startocare.bookingservice.dto.response.TravelLegQuoteResponse;
import com.startocare.bookingservice.enums.BookingStatus;
import com.startocare.bookingservice.enums.CompanionBookingDecision;
import com.startocare.bookingservice.enums.Role;
import com.startocare.bookingservice.enums.TravelPlan;
import com.startocare.bookingservice.events.PaymentSucceededEvent;
import com.startocare.bookingservice.security.JwtService;
import com.startocare.bookingservice.service.PaymentSucceededEventHandler;
import com.startocare.bookingservice.service.TravelPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingControllerIntegrationTest {

    private static final UUID COMPANION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PaymentSucceededEventHandler paymentSucceededEventHandler;

    @MockitoBean
    private TravelPricingService travelPricingService;

    @BeforeEach
    void configureTravelEstimate() {
        when(travelPricingService.calculate(
                any(UUID.class),
                any(TravelPlan.class),
                nullable(String.class),
                anyString(),
                any(BigDecimal.class)
        )).thenAnswer(invocation -> {
            BigDecimal serviceAmount = invocation.getArgument(4);
            return new TravelPricingService.TravelQuote(
                    BigDecimal.valueOf(150),
                    serviceAmount.add(BigDecimal.valueOf(150)),
                    List.of(
                            new TravelLegQuoteResponse(
                                    "COMPANION_TO_HOSPITAL", "Companion base", "Hospital",
                                    6875, new BigDecimal("6.875"), BigDecimal.valueOf(20),
                                    BigDecimal.valueOf(55), BigDecimal.valueOf(75)
                            ),
                            new TravelLegQuoteResponse(
                                    "HOSPITAL_TO_COMPANION", "Hospital", "Companion base",
                                    6875, new BigDecimal("6.875"), BigDecimal.valueOf(20),
                                    BigDecimal.valueOf(55), BigDecimal.valueOf(75)
                            )
                    )
            );
        });
    }

    @Test
    void getDurationPricing_returnsSixActiveDurations() throws Exception {
        mockMvc.perform(get("/api/bookings/pricing/durations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].durationMinutes").value(30))
                .andExpect(jsonPath("$[0].serviceAmount").value(69.00));
    }

    @Test
    void estimateBooking_returnsServiceAndTravelQuote() throws Exception {
        EstimateRequest request = new EstimateRequest(
                COMPANION_ID,
                180,
                TravelPlan.PLAN_1,
                "Apollo Hospital",
                "MG Road, Bengaluru",
                "Rohan Sharma",
                LocalDate.now().plusDays(5),
                LocalTime.of(10, 30),
                "Nagole, Hyderabad"
        );

        mockMvc.perform(post("/api/bookings/estimate")
                        .header("Authorization", "Bearer " + jwtService.generateToken(UUID.randomUUID(), Role.FAMILY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceAmount").value(240.00))
                .andExpect(jsonPath("$.travelAmount").value(150.00))
                .andExpect(jsonPath("$.totalAmount").value(390.00))
                .andExpect(jsonPath("$.travelQuoteStatus").value("QUOTED"))
                .andExpect(jsonPath("$.travelLegs.length()").value(2));
    }

    @Test
    void createBooking_requiresFamilyRole_andWaitsForPayment() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(
                COMPANION_ID,
                180,
                TravelPlan.PLAN_1,
                "Apollo Hospital",
                "MG Road, Bengaluru",
                "Rohan Sharma",
                LocalDate.now().plusDays(6),
                LocalTime.of(11, 0),
                "Nagole, Hyderabad",
                "+919876543210",
                56
        );

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + jwtService.generateToken(UUID.randomUUID(), Role.FAMILY))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.travelQuoteStatus").value("QUOTED"))
                .andExpect(jsonPath("$.serviceAmount").value(240.00))
                .andExpect(jsonPath("$.totalAmount").value(390.00));
    }

    @Test
    void familyUser_cannotAccessAnotherUsersBooking() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String ownerToken = jwtService.generateToken(ownerId, Role.FAMILY);
        String otherToken = jwtService.generateToken(UUID.randomUUID(), Role.FAMILY);

        String content = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(
                                COMPANION_ID,
                                240,
                                TravelPlan.PLAN_2,
                                "Fortis Hospital",
                                "Whitefield, Bengaluru",
                                "Kavya Rao",
                                LocalDate.now().plusDays(4),
                                LocalTime.of(9, 30),
                                "#18, Lake View, Bengaluru",
                                "+919876543211",
                                42
                        ))))
                .andReturn().getResponse().getContentAsString();

        String bookingId = objectMapper.readTree(content).get("id").asText();

        mockMvc.perform(get("/api/bookings/{bookingId}", bookingId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void selectedCompanion_canAcceptBooking_andAdminCannotConfirmPendingBooking() throws Exception {
        UUID familyId = UUID.randomUUID();
        String familyToken = jwtService.generateToken(familyId, Role.FAMILY);
        String adminToken = jwtService.generateToken(UUID.randomUUID(), Role.ADMIN);
        String companionToken = jwtService.generateToken(COMPANION_ID, Role.COMPANION);

        String bookingJson = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(
                                COMPANION_ID,
                                180,
                                TravelPlan.PLAN_1,
                                "Manipal Hospital",
                                "Varthur Road, Bengaluru",
                                "Nikhil Verma",
                                LocalDate.now().plusDays(7),
                                LocalTime.of(14, 0),
                                "Nagole, Bengaluru",
                                "+919876543212",
                                61
                        ))))
                .andReturn().getResponse().getContentAsString();

        String bookingId = objectMapper.readTree(bookingJson).get("id").asText();

        mockMvc.perform(patch("/api/bookings/{bookingId}/status", bookingId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookingStatusUpdateRequest(BookingStatus.CONFIRMED))))
                .andExpect(status().isConflict());

        settlePayment(bookingJson);
        mockMvc.perform(patch("/api/bookings/{bookingId}/companion-response", bookingId)
                        .header("Authorization", "Bearer " + companionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CompanionBookingResponseRequest(CompanionBookingDecision.ACCEPTED)
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"));
    }

    @Test
    void selectedCompanion_canDeclineBooking() throws Exception {
        String familyToken = jwtService.generateToken(UUID.randomUUID(), Role.FAMILY);
        String companionToken = jwtService.generateToken(COMPANION_ID, Role.COMPANION);
        String bookingJson = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(
                                COMPANION_ID,
                                60,
                                TravelPlan.PLAN_2,
                                "Apollo Hospital",
                                "Hyderabad, Telangana",
                                "Patient Example",
                                LocalDate.now().plusDays(2),
                                LocalTime.of(10, 0),
                                null,
                                null,
                                null
                        ))))
                .andReturn().getResponse().getContentAsString();
        String bookingId = objectMapper.readTree(bookingJson).get("id").asText();
        settlePayment(bookingJson);

        mockMvc.perform(patch("/api/bookings/{bookingId}/companion-response", bookingId)
                        .header("Authorization", "Bearer " + companionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CompanionBookingResponseRequest(CompanionBookingDecision.DECLINED)
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("DECLINED"));
    }

    @Test
    void onlySelectedCompanionCanRespondToBooking() throws Exception {
        String familyToken = jwtService.generateToken(UUID.randomUUID(), Role.FAMILY);
        String otherCompanionToken = jwtService.generateToken(UUID.randomUUID(), Role.COMPANION);
        String bookingJson = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(
                                COMPANION_ID,
                                60,
                                TravelPlan.PLAN_2,
                                "Apollo Hospital",
                                "Hyderabad, Telangana",
                                "Patient Example",
                                LocalDate.now().plusDays(2),
                                LocalTime.of(10, 0),
                                null,
                                null,
                                null
                        ))))
                .andReturn().getResponse().getContentAsString();
        String bookingId = objectMapper.readTree(bookingJson).get("id").asText();
        settlePayment(bookingJson);

        mockMvc.perform(patch("/api/bookings/{bookingId}/companion-response", bookingId)
                        .header("Authorization", "Bearer " + otherCompanionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CompanionBookingResponseRequest(CompanionBookingDecision.ACCEPTED)
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void companionCanListOnlyTheirPendingBookingRequests() throws Exception {
        UUID familyId = UUID.randomUUID();
        UUID selectedCompanionId = UUID.randomUUID();
        String familyToken = jwtService.generateToken(familyId, Role.FAMILY);
        String companionToken = jwtService.generateToken(selectedCompanionId, Role.COMPANION);
        String bookingJson = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(
                                selectedCompanionId,
                                60,
                                TravelPlan.PLAN_2,
                                "Apollo Hospital",
                                "Hyderabad, Telangana",
                                "Patient Example",
                                LocalDate.now().plusDays(2),
                                LocalTime.of(10, 0),
                                null,
                                null,
                                null
                        ))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        settlePayment(bookingJson);

        mockMvc.perform(get("/api/bookings/companion-requests")
                        .header("Authorization", "Bearer " + companionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].bookingStatus").value("PENDING"));
    }

    @Test
    void cancelBooking_followsValidRules() throws Exception {
        UUID familyId = UUID.randomUUID();
        String familyToken = jwtService.generateToken(familyId, Role.FAMILY);

        String bookingJson = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + familyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(
                                COMPANION_ID,
                                60,
                                TravelPlan.PLAN_1,
                                "KIMS Hospital",
                                "Hebbal, Bengaluru",
                                "Asha Patel",
                                LocalDate.now().plusDays(3),
                                LocalTime.of(15, 30),
                                "Hebbal, Bengaluru",
                                "+919876543213",
                                30
                        ))))
                .andReturn().getResponse().getContentAsString();

        settlePayment(bookingJson);
        String bookingId = objectMapper.readTree(bookingJson).get("id").asText();

        mockMvc.perform(patch("/api/bookings/{bookingId}/cancel", bookingId)
                        .header("Authorization", "Bearer " + familyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED"));
    }

    private void settlePayment(String bookingJson) throws Exception {
        var booking = objectMapper.readTree(bookingJson);
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                UUID.randomUUID(),
                UUID.fromString(booking.path("id").asText()),
                UUID.fromString(booking.path("familyUserId").asText()),
                "order_test",
                "pay_test",
                booking.path("totalAmount").decimalValue().movePointRight(2).longValueExact(),
                "INR",
                java.time.Instant.now()
        );
        paymentSucceededEventHandler.onPaymentSucceeded(objectMapper.writeValueAsString(event));
    }
}
