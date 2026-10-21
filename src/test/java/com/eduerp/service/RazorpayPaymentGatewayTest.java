package com.eduerp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.eduerp.exception.PaymentProviderException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RazorpayPaymentGatewayTest {

    @Test
    void verifiesPaymentSignatureAndRejectsTampering() throws Exception {
        RazorpayPaymentGateway gateway = configuredGateway("payment-secret", "webhook-secret");
        String valid = hmac("order_123|pay_123", "payment-secret");

        assertDoesNotThrow(() -> gateway.verifyPaymentSignature("order_123", "pay_123", valid));
        assertThrows(IllegalArgumentException.class,
                () -> gateway.verifyPaymentSignature("order_123", "pay_123", "tampered"));
    }

    @Test
    void verifiesWebhookSignatureAndRejectsTampering() throws Exception {
        RazorpayPaymentGateway gateway = configuredGateway("payment-secret", "webhook-secret");
        String payload = "{\"event\":\"payment.captured\"}";
        String valid = hmac(payload, "webhook-secret");

        assertDoesNotThrow(() -> gateway.verifyWebhookSignature(payload, valid));
        assertThrows(IllegalArgumentException.class,
                () -> gateway.verifyWebhookSignature(payload, "tampered"));
    }

    @Test
    void createsRazorpayOrderWithPaiseAndObjectNotes() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"order_test_123\",\"amount\":3700000,\"currency\":\"INR\",\"status\":\"created\"}");
        when(client.send(any(HttpRequest.class), any())).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            captured.set(request);
            return response;
        });

        RazorpayPaymentGateway gateway = configuredGateway(client, "payment-secret", "webhook-secret");
        PaymentGateway.PaymentOrder order = gateway.createOrder(123L, new BigDecimal("37000.00"), "idem-123", "EDUERP-INV-123");

        assertEquals("order_test_123", order.orderId());
        assertEquals(new BigDecimal("37000.00"), order.amount());
        String body = requestBody(captured.get());
        assertEquals("{\"amount\":3700000,\"currency\":\"INR\",\"receipt\":\"EDUERP-INV-123\",\"notes\":{\"invoice_id\":\"123\"}}", body);
    }

    @Test
    void exposesSafeProviderMetadataAndPreservesCauseForRejectedOrder() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(400);
        when(response.body()).thenReturn("{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"notes must be an object\"}}");
        when(client.send(any(HttpRequest.class), any())).thenAnswer(invocation -> response);

        RazorpayPaymentGateway gateway = configuredGateway(client, "payment-secret", "webhook-secret");
        PaymentProviderException exception = assertThrows(PaymentProviderException.class,
                () -> gateway.createOrder(123L, new BigDecimal("1000.00"), "idem-123", "EDUERP-INV-123"));

        assertEquals(400, exception.getStatusCode());
        assertEquals("BAD_REQUEST_ERROR", exception.getProviderCode());
        assertEquals("Razorpay HTTP 400 code=BAD_REQUEST_ERROR", exception.getCause().getMessage());
    }

    @Test
    void rejectsAmountsThatCannotBeRepresentedAsPositivePaise() throws Exception {
        RazorpayPaymentGateway gateway = configuredGateway("payment-secret", "webhook-secret");

        assertThrows(IllegalArgumentException.class,
                () -> gateway.createOrder(123L, new BigDecimal("0.001"), "idem-123", "EDUERP-INV-123"));
        assertThrows(IllegalArgumentException.class,
                () -> gateway.createOrder(123L, BigDecimal.ZERO, "idem-123", "EDUERP-INV-123"));
    }

    @Test
    void rejectsProviderOrderWithUnexpectedAmountOrStatus() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"id\":\"order_test_123\",\"amount\":20000,\"currency\":\"INR\",\"status\":\"paid\"}");
        when(client.send(any(HttpRequest.class), any())).thenAnswer(invocation -> response);

        RazorpayPaymentGateway gateway = configuredGateway(client, "payment-secret", "webhook-secret");
        PaymentProviderException exception = assertThrows(PaymentProviderException.class,
                () -> gateway.createOrder(123L, new BigDecimal("500.00"), "idem-invalid-order", "EDUERP-INV-123"));

        assertEquals("INVALID_ORDER", exception.getProviderCode());
    }

    @Test
    void reportsMissingBackendCredentialsAsSafeConfigurationError() throws Exception {
        RazorpayPaymentGateway gateway = configuredGateway("payment-secret", "webhook-secret");
        set(gateway, "keyId", "");

        PaymentProviderException exception = assertThrows(PaymentProviderException.class,
                () -> gateway.createOrder(123L, new BigDecimal("200.00"), "idem-missing", "EDUERP-INV-123"));

        assertEquals(503, exception.getStatusCode());
        assertEquals("CONFIGURATION_MISSING", exception.getProviderCode());
    }

    private RazorpayPaymentGateway configuredGateway(String keySecret, String webhookSecret) throws Exception {
        RazorpayPaymentGateway gateway = new RazorpayPaymentGateway(new ObjectMapper());
        configure(gateway, keySecret, webhookSecret);
        return gateway;
    }

    private RazorpayPaymentGateway configuredGateway(HttpClient client, String keySecret, String webhookSecret) throws Exception {
        RazorpayPaymentGateway gateway = new RazorpayPaymentGateway(new ObjectMapper(), client);
        configure(gateway, keySecret, webhookSecret);
        return gateway;
    }

    private void configure(RazorpayPaymentGateway gateway, String keySecret, String webhookSecret) throws Exception {
        set(gateway, "keyId", "rzp_test_key");
        set(gateway, "keySecret", keySecret);
        set(gateway, "webhookSecret", webhookSecret);
        set(gateway, "baseUrl", "https://api.razorpay.com/v1");
    }

    private void set(Object target, String field, String value) throws Exception {
        var member = target.getClass().getDeclaredField(field);
        member.setAccessible(true);
        member.set(target, value);
    }

    private String requestBody(HttpRequest request) throws Exception {
        CompletableFuture<String> result = new CompletableFuture<>();
        StringBuilder body = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer item) { body.append(StandardCharsets.UTF_8.decode(item)); }
            @Override public void onError(Throwable throwable) { result.completeExceptionally(throwable); }
            @Override public void onComplete() { result.complete(body.toString()); }
        });
        return result.get(1, TimeUnit.SECONDS);
    }

    private String hmac(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder result = new StringBuilder();
        for (byte value : mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))) result.append(String.format("%02x", value));
        return result.toString();
    }
}
