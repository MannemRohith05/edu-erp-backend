package com.eduerp.exception;

public class PaymentProviderException extends RuntimeException {
    private final int statusCode;
    private final String providerCode;

    public PaymentProviderException(String message, int statusCode, String providerCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.providerCode = providerCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getProviderCode() {
        return providerCode;
    }
}
