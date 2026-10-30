package com.startocare.userservice.controller;

import com.startocare.userservice.dto.request.ChangePasswordRequest;
import com.startocare.userservice.dto.request.LoginRequest;
import com.startocare.userservice.dto.request.RegisterRequest;
import com.startocare.userservice.dto.request.SendOtpRequest;
import com.startocare.userservice.dto.response.AuthResponse;
import com.startocare.userservice.entity.User;
import com.startocare.userservice.security.UserPrincipal;
import com.startocare.userservice.service.AuthService;
import com.startocare.userservice.service.OtpService;
import com.startocare.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final OtpService otpService;

    public AuthController(UserService userService, AuthService authService, OtpService otpService) {
        this.userService = userService;
        this.authService = authService;
        this.otpService = otpService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (!otpService.verifyOtp(request.email(), request.otp())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP");
        }
        User user = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createAuthResponse(user));
    }

    @PostMapping("/send-otp")
    public ResponseEntity<Void> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        otpService.sendOtp(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(principal.userId(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/testing")
    public String check(){
         return "Hello, Welcom to StartoCare";
    }
}
