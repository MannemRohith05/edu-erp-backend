package com.eduerp.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private static final int OTP_EXPIRY_SECONDS = 300;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    public String createOtp(String email) {
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        otpStore.put(normalize(email), new OtpEntry(otp, Instant.now().plusSeconds(OTP_EXPIRY_SECONDS)));
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
            throw new IllegalArgumentException("Invalid OTP");
        }

        otpStore.remove(key);
    }

    public int getExpirySeconds() {
        return OTP_EXPIRY_SECONDS;
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private record OtpEntry(String otp, Instant expiresAt) {
    }
}
