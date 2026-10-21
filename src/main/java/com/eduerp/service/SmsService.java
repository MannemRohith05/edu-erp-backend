package com.eduerp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class SmsService {
    private final HttpClient client = HttpClient.newHttpClient();
    @Value("${app.sms.provider:none}") private String provider;
    @Value("${app.sms.twilio.account-sid:}") private String accountSid;
    @Value("${app.sms.twilio.auth-token:}") private String authToken;
    @Value("${app.sms.twilio.from:}") private String fromNumber;

    public void sendOtp(String mobileNumber, String otp) {
        send(mobileNumber, "Your EduERP payment verification OTP is " + otp + ". It expires soon.");
    }

    public void sendPaymentConfirmation(String mobileNumber, String reference, java.math.BigDecimal amount, java.math.BigDecimal balance) {
        send(mobileNumber, "EduERP payment confirmed: INR " + amount.toPlainString() + ". Balance INR " + balance.toPlainString() + ". Ref " + reference + ".");
    }

    private void send(String mobileNumber, String body) {
        if (!"twilio".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("SMS provider is not configured");
        }
        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            throw new IllegalStateException("SMS provider credentials are not configured");
        }
        try {
            String form = "To=" + encode(mobileNumber) + "&From=" + encode(fromNumber) + "&Body=" + encode(body);
            String auth = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("SMS provider rejected submission");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SMS provider request interrupted");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("SMS provider request failed");
        }
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
