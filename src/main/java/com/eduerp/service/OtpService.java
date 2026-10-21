package com.eduerp.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.auth.otp.length:6}")
    private int otpLength = 6;

    @Value("${app.auth.otp.expiration-minutes:5}")
    private int otpExpirationMinutes = 5;

    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    public String createOtp(String email) {
        String key = normalize(email);
        OtpEntry existing = otpStore.get(key);
        if (existing != null && existing.createdAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(Instant.now())) {
            throw new IllegalArgumentException("Please wait before requesting another OTP");
        }
        if (otpLength < 4 || otpLength > 8) {
            throw new IllegalStateException("OTP length must be between 4 and 8 digits");
        }
        int upperBound = (int) Math.pow(10, otpLength);
        String otp = String.format("%0" + otpLength + "d", RANDOM.nextInt(upperBound));
        Instant now = Instant.now();
        otpStore.put(key, new OtpEntry(otp, now.plusSeconds(otpExpirationMinutes * 60L), now, 0));
        return otp;
    }

    public void verifyOtp(String email, String otp) {
        String key = normalize(email);
        OtpEntry entry = otpStore.get(key);

        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            otpStore.remove(key);
            throw new IllegalArgumentException("OTP expired. Please login again to receive a new OTP.");
        }

        if (!entry.otp().equals(otp)) {
            otpStore.computeIfPresent(key, (ignored, current) -> {
                int attempts = current.failedAttempts() + 1;
                return attempts >= MAX_ATTEMPTS ? null : new OtpEntry(
                        current.otp(), current.expiresAt(), current.createdAt(), attempts);
            });
            throw new IllegalArgumentException("Invalid OTP");
        }

        otpStore.remove(key);
    }

    public int getExpirySeconds() {
        return otpExpirationMinutes * 60;
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private record OtpEntry(String otp, Instant expiresAt, Instant createdAt, int failedAttempts) {
    }
}
