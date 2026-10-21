package com.eduerp.service;

import java.math.BigDecimal;

public interface PaymentGateway {
    PaymentOrder createOrder(Long invoiceId, BigDecimal amount, String idempotencyKey, String receipt);
    void verifyPaymentSignature(String orderId, String paymentId, String signature);
    default void verifyPaymentDetails(String orderId, String paymentId, BigDecimal amount) { }
    default OrderCheck checkOrder(String orderId, BigDecimal amount) { return new OrderCheck(true, false, false, null); }
    void verifyWebhookSignature(String payload, String signature);
    void refund(String paymentId, java.math.BigDecimal amount, String idempotencyKey);

    /** Public provider identifier used by Checkout; never a secret. */
    default String publicKeyId() { return null; }

    record PaymentOrder(String provider, String orderId, String keyId, BigDecimal amount, String currency) { }
    record OrderCheck(boolean usable, boolean definitive, boolean paid, String paymentId) {
        public OrderCheck(boolean usable, boolean definitive) {
            this(usable, definitive, false, null);
        }
    }
}
