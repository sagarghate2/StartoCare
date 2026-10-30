package com.startocare.userservice.service;

import com.startocare.userservice.dto.response.AuthResponse;
import com.startocare.userservice.dto.response.UserResponse;
import com.startocare.userservice.entity.User;
import com.startocare.userservice.exception.InvalidCredentialsException;
import com.startocare.userservice.repository.UserRepository;
import com.startocare.userservice.security.JwtService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmail(UserService.normalizeEmail(email))
                .filter(candidate -> passwordEncoder.matches(password, candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return createAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse createAuthResponse(User user) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs(), UserResponse.from(user));
    }
}
