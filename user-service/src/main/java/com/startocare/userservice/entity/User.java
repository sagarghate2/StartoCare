package com.startocare.userservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.startocare.userservice.enums.CompanionVerificationStatus;
import com.startocare.userservice.enums.Role;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        indexes = @Index(name = "idx_users_role", columnList = "role")
)
public class User {

    @Id
    @UuidGenerator
    private UUID id;

    @NotBlank
    @Size(max = 120)
    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @NotBlank
    @Email
    @Size(max = 254)
    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "phone_number", length = 13)
    private String phoneNumber;

    @NotBlank
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "companion_verification_status", nullable = false, length = 20)
    private CompanionVerificationStatus companionVerificationStatus;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String fullName, String email, String phoneNumber, String passwordHash, Role role) {
        this.fullName = fullName;
        this.email = normalizeEmail(email);
        this.phoneNumber = normalizePhoneNumber(phoneNumber);
        this.passwordHash = passwordHash;
        this.role = role;
        this.companionVerificationStatus = CompanionVerificationStatus.NOT_APPLICABLE;
        this.enabled = true;
        this.tokenVersion = 0;
    }

    @PrePersist
    void onCreate() {
        email = normalizeEmail(email);
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        email = normalizeEmail(email);
        updatedAt = Instant.now();
    }

    private static String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizePhoneNumber(String value) {
        return value == null ? null : value.trim();
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public CompanionVerificationStatus getCompanionVerificationStatus() {
        return companionVerificationStatus;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void updateFullName(String fullName) {
        this.fullName = fullName.trim();
    }

    public void updatePhoneNumber(String phoneNumber) {
        this.phoneNumber = normalizePhoneNumber(phoneNumber);
    }

    public void updatePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.tokenVersion++;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
