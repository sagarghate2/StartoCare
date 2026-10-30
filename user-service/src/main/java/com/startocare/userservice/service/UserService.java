package com.startocare.userservice.service;

import com.startocare.userservice.dto.request.RegisterRequest;
import com.startocare.userservice.entity.User;
import com.startocare.userservice.enums.Role;
import com.startocare.userservice.exception.DuplicateEmailException;
import com.startocare.userservice.exception.InvalidCredentialsException;
import com.startocare.userservice.exception.ResourceNotFoundException;
import com.startocare.userservice.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }

        User user = new User(
                request.fullName().trim(),
                normalizedEmail,
                request.phoneNumber(),
                passwordEncoder.encode(request.password()),
                Role.FAMILY
        );
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateEmailException();
        }
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public User updateProfile(UUID id, String fullName, String phoneNumber) {
        User user = getById(id);
        user.updateFullName(fullName);
        if (phoneNumber != null) {
            user.updatePhoneNumber(phoneNumber);
        }
        return user;
    }

    @Transactional
    public void changePassword(UUID id, String currentPassword, String newPassword) {
        User user = getById(id);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        user.updatePasswordHash(passwordEncoder.encode(newPassword));
    } 

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
