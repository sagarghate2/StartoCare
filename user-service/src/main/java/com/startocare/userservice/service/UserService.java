package com.startocare.userservice.service;

import com.startocare.userservice.dto.request.RegisterRequest;
import com.startocare.userservice.dto.response.CompanionLocationResponse;
import com.startocare.userservice.dto.response.CompanionSearchResponse;
import com.startocare.userservice.entity.User;
import com.startocare.userservice.enums.CompanionVerificationStatus;
import com.startocare.userservice.enums.Role;
import com.startocare.userservice.exception.DuplicateEmailException;
import com.startocare.userservice.exception.InvalidCredentialsException;
import com.startocare.userservice.exception.ResourceNotFoundException;
import com.startocare.userservice.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {

    private static final double COMPANION_SEARCH_RADIUS_KM = 8.0;
    private static final double EARTH_RADIUS_KM = 6371.0088;

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
        Role role = request.role() == null ? Role.FAMILY : request.role();
        if (role == Role.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ADMIN accounts must be provisioned by a trusted administrator"
            );
        }

        User user = new User(
                request.fullName().trim(),
                normalizedEmail,
                request.phoneNumber(),
                passwordEncoder.encode(request.password()),
                role
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
    public User applyAsCompanion(
            UUID id,
            String serviceLocationAddress,
            double latitude,
            double longitude
    ) {
        validateCoordinates(latitude, longitude);
        User user = getById(id);
        if (user.getRole() != Role.COMPANION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Register with role COMPANION first");
        }
        user.applyAsCompanion(serviceLocationAddress, latitude, longitude);
        return user;
    }

    @Transactional
    public User updateCompanionLocation(
            UUID id,
            String serviceLocationAddress,
            double latitude,
            double longitude
    ) {
        validateCoordinates(latitude, longitude);
        User user = getById(id);
        if (user.getRole() != Role.COMPANION
                || user.getCompanionVerificationStatus() == CompanionVerificationStatus.NOT_APPLICABLE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only COMPANION accounts may update this location");
        }
        if (user.getCompanionVerificationStatus() == CompanionVerificationStatus.VERIFIED) {
            user.updateCompanionVerificationStatus(CompanionVerificationStatus.PENDING);
        }
        user.updateServiceLocation(serviceLocationAddress, latitude, longitude);
        return user;
    }

    @Transactional(readOnly = true)
    public List<User> getPendingCompanionApplications() {
        return userRepository.findByRoleAndCompanionVerificationStatusOrderByCreatedAtAsc(
                Role.COMPANION,
                CompanionVerificationStatus.PENDING
        );
    }

    @Transactional
    public User updateCompanionVerification(UUID companionId, CompanionVerificationStatus status) {
        User companion = getById(companionId);
        if (companion.getRole() != Role.COMPANION
                || companion.getCompanionVerificationStatus() == CompanionVerificationStatus.NOT_APPLICABLE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Companion application not found");
        }
        if (status == CompanionVerificationStatus.VERIFIED
                && (companion.getServiceLocationAddress() == null
                || companion.getServiceLatitude() == null
                || companion.getServiceLongitude() == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A companion service location is required before verification"
            );
        }
        companion.updateCompanionVerificationStatus(status);
        return companion;
    }

    @Transactional(readOnly = true)
    public List<CompanionSearchResponse> searchVerifiedCompanions(double latitude, double longitude) {
        validateCoordinates(latitude, longitude);
        double latitudeDelta = COMPANION_SEARCH_RADIUS_KM / 111.32;
        double longitudeScale = Math.cos(Math.toRadians(latitude));
        double longitudeDelta = Math.abs(longitudeScale) < 0.000001
                ? 180.0
                : Math.min(180.0, COMPANION_SEARCH_RADIUS_KM / (111.32 * Math.abs(longitudeScale)));

        List<User> candidates = userRepository
                .findByRoleAndCompanionVerificationStatusAndEnabledTrueAndServiceLatitudeBetweenAndServiceLongitudeBetween(
                        Role.COMPANION,
                        CompanionVerificationStatus.VERIFIED,
                        Math.max(-90.0, latitude - latitudeDelta),
                        Math.min(90.0, latitude + latitudeDelta),
                        Math.max(-180.0, longitude - longitudeDelta),
                        Math.min(180.0, longitude + longitudeDelta)
                );

        return candidates.stream()
                .map(companion -> new NearbyCompanion(
                        companion,
                        distanceKm(latitude, longitude, companion.getServiceLatitude(), companion.getServiceLongitude())
                ))
                .filter(companion -> companion.distanceKm() <= COMPANION_SEARCH_RADIUS_KM)
                .sorted(Comparator.comparingDouble(NearbyCompanion::distanceKm))
                .map(companion -> new CompanionSearchResponse(
                        companion.user().getId(),
                        companion.user().getFullName(),
                        "GENERAL_COMPANION",
                        Math.round(companion.distanceKm() * 10.0) / 10.0
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanionLocationResponse getVerifiedCompanionLocation(UUID companionId) {
        User companion = getById(companionId);
        if (companion.getRole() != Role.COMPANION
                || companion.getCompanionVerificationStatus() != CompanionVerificationStatus.VERIFIED
                || !companion.isEnabled()
                || companion.getServiceLocationAddress() == null
                || companion.getServiceLatitude() == null
                || companion.getServiceLongitude() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Verified companion location not found");
        }
        return new CompanionLocationResponse(
                companion.getId(),
                companion.getServiceLocationAddress(),
                companion.getServiceLatitude(),
                companion.getServiceLongitude()
        );
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

    private void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0
                || !Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid latitude and longitude are required");
        }
    }

    private double distanceKm(double latitude1, double longitude1, Double latitude2, Double longitude2) {
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(latitudeDelta / 2.0) * Math.sin(latitudeDelta / 2.0)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(longitudeDelta / 2.0) * Math.sin(longitudeDelta / 2.0);
        return EARTH_RADIUS_KM * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }

    private record NearbyCompanion(User user, double distanceKm) {
    }
}
