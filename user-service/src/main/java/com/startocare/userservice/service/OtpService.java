package com.startocare.userservice.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OtpService {

    private static final long OTP_LIFESPAN_MILLIS = 300_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, OtpEntry> otpByEmail = new ConcurrentHashMap<>();
    private final JavaMailSender mailSender;

    public OtpService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        Instant now = Instant.now();
        otpByEmail.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));

        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        OtpEntry otpEntry = new OtpEntry(code, now.plusMillis(OTP_LIFESPAN_MILLIS));
        otpByEmail.put(normalizedEmail, otpEntry);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(normalizedEmail);
        message.setSubject("StartoCare email verification");
        message.setText("Your StartoCare verification code is: " + code);
        try {
            mailSender.send(message);
        } catch (RuntimeException exception) {
            otpByEmail.remove(normalizedEmail, otpEntry);
            throw exception;
        }
    }

    public boolean verifyOtp(String email, String code) {
        if (email == null || code == null) {
            return false;
        }

        String normalizedEmail = normalizeEmail(email);
        AtomicBoolean verified = new AtomicBoolean(false);
        Instant now = Instant.now();
        otpByEmail.compute(normalizedEmail, (key, otpEntry) -> {
            if (otpEntry == null || !now.isBefore(otpEntry.expiresAt())) {
                return null;
            }
            if (otpEntry.code().equals(code)) {
                verified.set(true);
                return null;
            }
            return otpEntry;
        });
        return verified.get();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record OtpEntry(String code, Instant expiresAt) {
    }
}
