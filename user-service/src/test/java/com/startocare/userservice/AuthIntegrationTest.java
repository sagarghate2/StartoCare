package com.startocare.userservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.startocare.userservice.entity.User;
import com.startocare.userservice.enums.CompanionVerificationStatus;
import com.startocare.userservice.enums.Role;
import com.startocare.userservice.repository.UserRepository;
import com.startocare.userservice.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String EMAIL = "sagar@example.com";
    private static final String PASSWORD = "A-Strong-Password-123";
    private static final String TEST_JWT_SECRET = generateTestJwtSecret();

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("app.internal-api-key", () -> "test-companion-api-key");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void clearUsers() {
        userRepository.deleteAll();
        reset(mailSender);
    }

    @Test
    void companionApplicationRequiresAdminVerificationAndSearchDoesNotExposeBaseAddress() throws Exception {
        User applicant = userRepository.saveAndFlush(new User(
                "Companion Applicant",
                "companion@example.com",
                "+919876543210",
                passwordEncoder.encode(PASSWORD),
                Role.COMPANION
        ));
        User family = userRepository.saveAndFlush(new User(
                "Patient Family",
                "family@example.com",
                "+919876543211",
                passwordEncoder.encode(PASSWORD),
                Role.FAMILY
        ));
        User admin = userRepository.saveAndFlush(new User(
                "Administrator",
                "admin@example.com",
                "+919876543212",
                passwordEncoder.encode(PASSWORD),
                Role.ADMIN
        ));
        User distantCompanion = new User(
                "Distant Companion",
                "distant@example.com",
                "+919876543213",
                passwordEncoder.encode(PASSWORD),
                Role.COMPANION
        );
        distantCompanion.applyAsCompanion("Far from search point", 17.60, 78.5013);
        distantCompanion.updateCompanionVerificationStatus(CompanionVerificationStatus.VERIFIED);
        userRepository.saveAndFlush(distantCompanion);

        String oldFamilyToken = jwtService.generateToken(applicant);
        String familyToken = jwtService.generateToken(family);
        String adminToken = jwtService.generateToken(admin);

        MvcResult applicationResult = mockMvc.perform(put("/api/users/me/companion-profile")
                        .header("Authorization", "Bearer " + oldFamilyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceLocationAddress": "Secunderabad Railway Station, Hyderabad",
                                  "latitude": 17.4344,
                                  "longitude": 78.5013
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"))
                .andReturn();

        JsonNode application = objectMapper.readTree(applicationResult.getResponse().getContentAsString());
        String companionId = application.path("id").asText();

        mockMvc.perform(get("/api/companions/search")
                        .param("latitude", "17.4344")
                        .param("longitude", "78.5013")
                        .header("Authorization", "Bearer " + familyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/admin/companions/pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(companionId));

        mockMvc.perform(patch("/api/admin/companions/{companionId}/verification", companionId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VERIFIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"));

        mockMvc.perform(get("/api/companions/search")
                        .param("latitude", "17.4344")
                        .param("longitude", "78.5013")
                        .header("Authorization", "Bearer " + familyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(companionId))
                .andExpect(jsonPath("$[0].service").value("GENERAL_COMPANION"))
                .andExpect(jsonPath("$[0].serviceLocationAddress").doesNotExist())
                .andExpect(jsonPath("$[0].latitude").doesNotExist());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + oldFamilyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COMPANION"));

        mockMvc.perform(put("/api/users/me/companion-location")
                        .header("Authorization", "Bearer " + oldFamilyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceLocationAddress": "Nagole, Hyderabad",
                                  "latitude": 17.4065,
                                  "longitude": 78.5572
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("PENDING"));
        mockMvc.perform(get("/api/companions/search")
                        .param("latitude", "17.4344")
                        .param("longitude", "78.5013")
                        .header("Authorization", "Bearer " + familyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testingEndpointIsPublicEvenWhenAnInvalidBearerTokenIsSent() throws Exception {
        mockMvc.perform(get("/api/auth/testing")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("Hello, Welcom to StartoCare"));
    }

    @Test
    void registersUserWithNormalizedEmailAndBcryptPassword() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("  Sagar@Example.com  ", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.user.id").exists())
                .andExpect(jsonPath("$.user.fullName").value("Sagar Ghate"))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.phoneNumber").value("+919876543210"))
                .andExpect(jsonPath("$.user.role").value("FAMILY"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn();

        JsonNode registrationResponse = objectMapper.readTree(result.getResponse().getContentAsString());
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + registrationResponse.path("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));

        User savedUser = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(savedUser.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, savedUser.getPasswordHash())).isTrue();
        assertThat(savedUser.getPhoneNumber()).isEqualTo("+919876543210");
    }

    @Test
    void sendOtpIsPublicAndSendsSixDigitCodeEvenWithInvalidBearerToken() throws Exception {
        mockMvc.perform(post("/api/auth/send-otp")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendOtpBody(EMAIL))))
                .andExpect(status().isAccepted());

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getTo()).containsExactly(EMAIL);
        assertThat(messageCaptor.getValue().getText()).matches(
                "Your StartoCare verification code is: \\d{6}");
    }

    @Test
    void registrationRejectsInvalidOtpWithBadRequestReason() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationBody("Sagar Ghate", "unverified@example.com",
                                        "+919876543210", PASSWORD, "000000", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired OTP"));
    }

    @Test
    void rejectsDuplicateEmailWithConflict() throws Exception {
        registerUser();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("SAGAR@example.com", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
    }

    @Test
    void rejectsInvalidRegistrationInput() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":" ","email":"not-an-email","phoneNumber":"9876543210","password":"weak"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fullName").exists())
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.phoneNumber").exists())
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void publicRegistrationCannotAssignAdminRole() throws Exception {
        String otp = sendOtpAndGetCode(EMAIL);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationBody("Sagar Ghate", EMAIL, "+919876543210",
                                        PASSWORD, otp, "ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "ADMIN accounts must be provisioned by a trusted administrator"));
    }

    @Test
    void publicRegistrationCanCreatePendingCompanionAccount() throws Exception {
        String email = "newcompanion@example.com";
        String otp = sendOtpAndGetCode(email);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationBody("New Companion", email, "+919876543214",
                                        PASSWORD, otp, "COMPANION"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("COMPANION"))
                .andExpect(jsonPath("$.user.companionVerificationStatus").value("PENDING"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void successfulLoginReturnsSignedJwtWithUserSubjectAndRole() throws Exception {
        registerUser();

        MvcResult result = login(PASSWORD);
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(response.path("user").path("email").asText()).isEqualTo(EMAIL);
        assertThat(response.path("user").path("passwordHash").isMissingNode()).isTrue();

        Jws<Claims> claims = jwtService.parseToken(response.path("token").asText());
        assertThat(UUID.fromString(claims.getPayload().getSubject())).isEqualTo(
                UUID.fromString(response.path("user").path("id").asText()));
        assertThat(claims.getPayload().get("role", String.class)).isEqualTo("FAMILY");
    }

    @Test
    void rejectsInvalidCredentialsWithGenericMessage() throws Exception {
        registerUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unknown@example.com","password":"A-Strong-Password-123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void protectedEndpointRejectsMissingAndInvalidTokens() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired bearer token"));
    }

    @Test
    void protectedEndpointReturnsCurrentUserForValidToken() throws Exception {
        registerUser();
        JsonNode loginResponse = objectMapper.readTree(login(PASSWORD).getResponse().getContentAsString());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + loginResponse.path("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("FAMILY"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void profileUpdateCannotChangeRoleVerificationOrEnabledStatus() throws Exception {
        registerUser();
        JsonNode loginResponse = objectMapper.readTree(login(PASSWORD).getResponse().getContentAsString());
        String token = loginResponse.path("token").asText();

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Updated Name","phoneNumber":"+919123456789","role":"ADMIN",
                                 "companionVerificationStatus":"VERIFIED","enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.phoneNumber").value("+919123456789"))
                .andExpect(jsonPath("$.role").value("FAMILY"))
                .andExpect(jsonPath("$.companionVerificationStatus").value("NOT_APPLICABLE"))
                .andExpect(jsonPath("$.enabled").value(true));

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getRole().name()).isEqualTo("FAMILY");
        assertThat(user.getCompanionVerificationStatus().name()).isEqualTo("NOT_APPLICABLE");
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getPhoneNumber()).isEqualTo("+919123456789");
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndRevokesExistingTokens() throws Exception {
        registerUser();
        JsonNode loginResponse = objectMapper.readTree(login(PASSWORD).getResponse().getContentAsString());
        String token = loginResponse.path("token").asText();
        String newPassword = "Another-Strong-Password-456";

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"incorrect","newPassword":"Another-Strong-Password-456"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PasswordChangeBody(PASSWORD, newPassword))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginBody(EMAIL, newPassword))))
                .andExpect(status().isOk());
    }

    private void registerUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(EMAIL, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String registrationJson(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(
                new RegistrationBody("Sagar Ghate", email, "+919876543210",
                        password, sendOtpAndGetCode(email), null));
    }

    private String sendOtpAndGetCode(String email) throws Exception {
        reset(mailSender);
        mockMvc.perform(post("/api/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendOtpBody(email))))
                .andExpect(status().isAccepted());

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        Matcher matcher = Pattern.compile("\\d{6}").matcher(messageCaptor.getValue().getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    private String loginJson(String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginBody(EMAIL, password));
    }

    private record RegistrationBody(
            String fullName, String email, String phoneNumber, String password, String otp, String role
    ) {
    }

    private record SendOtpBody(String email) {
    }

    private record LoginBody(String email, String password) {
    }

    private record PasswordChangeBody(String currentPassword, String newPassword) {
    }

    private static String generateTestJwtSecret() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
