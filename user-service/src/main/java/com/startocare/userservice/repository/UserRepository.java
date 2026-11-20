package com.startocare.userservice.repository;

import com.startocare.userservice.entity.User;
import com.startocare.userservice.enums.CompanionVerificationStatus;
import com.startocare.userservice.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    List<User> findByRoleAndCompanionVerificationStatusAndEnabledTrueAndServiceLatitudeBetweenAndServiceLongitudeBetween(
            Role role,
            CompanionVerificationStatus verificationStatus,
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude
    );

    List<User> findByRoleAndCompanionVerificationStatusOrderByCreatedAtAsc(
            Role role,
            CompanionVerificationStatus verificationStatus
    );
}
