package com.eduerp.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OtpServiceTest {

    @Test
    void otpIsSixDigitsAndSingleUse() {
        OtpService service = new OtpService();
        String otp = service.createOtp("student@example.com");

        assertEquals(6, otp.length());
        assertDoesNotThrow(() -> service.verifyOtp("student@example.com", otp));
        assertThrows(IllegalArgumentException.class, () -> service.verifyOtp("student@example.com", otp));
    }

    @Test
    void resendIsRateLimited() {
        OtpService service = new OtpService();
        service.createOtp("teacher@example.com");

        assertThrows(IllegalArgumentException.class, () -> service.createOtp("teacher@example.com"));
    }
}
