package com.eduerp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.eduerp.exception.PaymentProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class RazorpayPaymentGateway implements PaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(RazorpayPaymentGateway.class);
    private static final String CURRENCY = "INR";
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${razorpay.key-id:}") private String keyId;
    @Value("${razorpay.key-secret:}") private String keySecret;
    @Value("${razorpay.webhook-secret:}") private String webhookSecret;
    @Value("${razorpay.base-url:https://api.razorpay.com/v1}") private String baseUrl;

    @Autowired
    public RazorpayPaymentGateway(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newHttpClient());
    }

    RazorpayPaymentGateway(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String publicKeyId() {
        return keyId;
    }

    @Override
    public PaymentOrder createOrder(Long invoiceId, BigDecimal amount, String idempotencyKey, String receipt) {
        requireConfigured();
        BigDecimal normalizedAmount = validateOrderInputs(invoiceId, amount, idempotencyKey, receipt);
        try {
            ObjectNode order = objectMapper.createObjectNode()
                    .put("amount", normalizedAmount.movePointRight(2).longValueExact())
                    .put("currency", CURRENCY)
                    .put("receipt", receipt);
            order.putObject("notes").put("invoice_id", String.valueOf(invoiceId));
            String body = order.toString();
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/orders"))
                    .header("Authorization", basicAuth())
                    .header("Content-Type", "application/json")
                    .header("X-Razorpay-Idempotency", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw providerError("Razorpay order creation rejected", response);
            }
            JsonNode json = objectMapper.readTree(response.body());
            String orderId = json.path("id").asText();
            if (orderId.isBlank()) {
                throw new PaymentProviderException("Payment provider returned an invalid order", 502, null,
                        new IllegalStateException("Razorpay order response did not contain an id"));
            }
            long providerAmount = json.path("amount").asLong(-1);
            String providerCurrency = json.path("currency").asText();
            String providerStatus = json.path("status").asText();
            if (providerAmount != normalizedAmount.movePointRight(2).longValueExact()
                    || !CURRENCY.equalsIgnoreCase(providerCurrency)
                    || !"created".equalsIgnoreCase(providerStatus)) {
                log.warn("Razorpay returned unusable order: orderId={}, amountPaise={}, currency={}, status={}",
                        orderId, providerAmount, providerCurrency, providerStatus);
                throw new PaymentProviderException("Payment provider returned an unusable order", 502, "INVALID_ORDER",
                        new IllegalStateException("Razorpay order response failed amount, currency, or status validation"));
            }
            log.info("Razorpay order created: orderId={}, amountPaise={}, currency={}, status={}",
                    orderId, providerAmount, providerCurrency, providerStatus);
            return new PaymentOrder("RAZORPAY", orderId, keyId, normalizedAmount, CURRENCY);
        } catch (PaymentProviderException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Razorpay order request interrupted: type={}", ex.getClass().getSimpleName());
            throw new PaymentProviderException("Unable to create payment order", 502, null, ex);
        } catch (Exception ex) {
            log.warn("Razorpay order request failed: type={}, message={}", ex.getClass().getSimpleName(), safeMessage(ex));
            throw new PaymentProviderException("Unable to create payment order", 502, null, ex);
        }
    }

    @Override
    public OrderCheck checkOrder(String orderId, BigDecimal amount) {
        requireConfigured();
        if (orderId == null || orderId.isBlank() || !orderId.matches("[A-Za-z0-9._-]+") || amount == null) return new OrderCheck(false, true);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/orders/" + orderId))
                    .header("Authorization", basicAuth()).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return new OrderCheck(false, true);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw providerError("Razorpay order lookup failed", response);
            }
            JsonNode json = objectMapper.readTree(response.body());
            long providerAmount = json.path("amount").asLong(-1);
            String providerCurrency = json.path("currency").asText();
            String providerStatus = json.path("status").asText();
            long expectedAmountPaise = amount.movePointRight(2).longValueExact();

            boolean amountMatches = providerAmount == expectedAmountPaise;
            boolean currencyMatches = CURRENCY.equalsIgnoreCase(providerCurrency);
            boolean idMatches = orderId.equals(json.path("id").asText());

            if (!idMatches || !amountMatches || !currencyMatches) {
                return new OrderCheck(false, true);
            }

            if ("paid".equalsIgnoreCase(providerStatus)) {
                String paymentId = fetchCapturedPaymentId(orderId, expectedAmountPaise);
                return new OrderCheck(false, true, true, paymentId);
            }

            boolean usable = "created".equalsIgnoreCase(providerStatus);
            return new OrderCheck(usable, true);
        } catch (PaymentProviderException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderException("Unable to validate payment order", 502, null, ex);
        } catch (Exception ex) {
            throw new PaymentProviderException("Unable to validate payment order", 502, null, ex);
        }
    }

    private String fetchCapturedPaymentId(String orderId, long expectedAmountPaise) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/orders/" + orderId + "/payments"))
                    .header("Authorization", basicAuth()).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode items = objectMapper.readTree(response.body()).path("items");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String status = item.path("status").asText();
                        long amt = item.path("amount").asLong(-1);
                        if (("captured".equalsIgnoreCase(status) || "authorized".equalsIgnoreCase(status))
                                && (expectedAmountPaise <= 0 || amt == expectedAmountPaise)) {
                            return item.path("id").asText();
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to fetch captured payments for order {}: {}", orderId, safeMessage(ex));
        }
        return null;
    }

    private BigDecimal validateOrderInputs(Long invoiceId, BigDecimal amount, String idempotencyKey, String receipt) {
        if (invoiceId == null || invoiceId <= 0) throw new IllegalArgumentException("Invalid invoice reference");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Payment amount must be positive");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency key is required");
        if (receipt == null || receipt.isBlank() || receipt.length() > 40 || !receipt.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid payment receipt reference");
        }
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.movePointRight(2).longValueExact() <= 0) {
                throw new IllegalArgumentException("Payment amount must be positive");
            }
            return normalized;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Payment amount must have at most two decimal places", ex);
        }
    }

    private PaymentProviderException providerError(String message, HttpResponse<String> response) {
        String code = null;
        String description = null;
        try {
            JsonNode error = objectMapper.readTree(response.body()).path("error");
            code = textOrNull(error, "code");
            description = textOrNull(error, "description");
        } catch (Exception ignored) {
            // The provider response is diagnostic input only; do not expose or log its raw body.
        }
        log.warn("{}: status={}, code={}, description={}", message, response.statusCode(), code, safeMessage(description));
        return new PaymentProviderException("Payment provider rejected the order", response.statusCode(), code,
                new IllegalStateException("Razorpay HTTP " + response.statusCode() + (code == null ? "" : " code=" + code)));
    }

    private String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private String safeMessage(Throwable ex) {
        return safeMessage(ex == null ? null : ex.getMessage());
    }

    private String safeMessage(String value) {
        if (value == null) return "none";
        String sanitized = value.replaceAll("[\\r\\n]+", " ");
        return sanitized.substring(0, Math.min(sanitized.length(), 240));
    }

    @Override
    public void verifyPaymentSignature(String orderId, String paymentId, String signature) {
        requireConfigured();
        verify(orderId + "|" + paymentId, signature, keySecret);
    }

    @Override
    public void verifyPaymentDetails(String orderId, String paymentId, BigDecimal expectedAmount) {
        requireConfigured();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/payments/" + paymentId))
                    .header("Authorization", basicAuth()).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalArgumentException("Payment provider payment lookup failed");
            JsonNode json = objectMapper.readTree(response.body());
            BigDecimal actual = BigDecimal.valueOf(json.path("amount").asLong()).movePointLeft(2);
            String status = json.path("status").asText();
            if (!orderId.equals(json.path("order_id").asText()) || !"INR".equalsIgnoreCase(json.path("currency").asText()) || actual.compareTo(expectedAmount) != 0)
                throw new IllegalArgumentException("Payment provider payment details do not match the order");
            if (!"captured".equalsIgnoreCase(status) && !"authorized".equalsIgnoreCase(status))
                throw new IllegalArgumentException("Payment provider payment status is not captured or authorized: " + status);
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalArgumentException("Payment provider payment lookup interrupted"); }
        catch (java.io.IOException ex) { throw new IllegalArgumentException("Unable to verify payment provider details"); }
    }

    @Override
    public void verifyWebhookSignature(String payload, String signature) {
        requireConfigured();
        verify(payload, signature, webhookSecret);
    }

    @Override
    public void refund(String paymentId, BigDecimal amount, String idempotencyKey) {
        requireConfigured();
        try {
            String body = objectMapper.createObjectNode().put("amount", amount.movePointRight(2).longValueExact()).toString();
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/payments/" + paymentId + "/refund"))
                    .header("Authorization", basicAuth()).header("Content-Type", "application/json")
                    .header("X-Razorpay-Idempotency", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalArgumentException("Payment refund failed");
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalArgumentException("Payment refund interrupted"); }
        catch (Exception ex) { throw new IllegalArgumentException("Unable to refund payment"); }
    }

    private void verify(String payload, String provided, String secret) {
        if (provided == null || provided.isBlank() || secret == null || secret.isBlank()
                || !constantTimeEquals(hexHmac(payload, secret), provided)) {
            throw new IllegalArgumentException("Invalid payment signature");
        }
    }

    private String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
    }

    private void requireConfigured() {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new PaymentProviderException("Razorpay credentials are not configured", 503, "CONFIGURATION_MISSING",
                    new IllegalStateException("Razorpay backend credentials are missing"));
        }
    }

    private String hexHmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder result = new StringBuilder();
            for (byte value : mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception ex) { throw new IllegalStateException("Unable to verify payment signature", ex); }
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
