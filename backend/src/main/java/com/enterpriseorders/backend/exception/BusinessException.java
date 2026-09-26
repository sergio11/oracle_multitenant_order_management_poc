package com.enterpriseorders.backend.exception;

/**
 * Functional business exception (SRS §54).
 *
 * <p>Carries a functional error code (e.g. INSUFFICIENT_INVENTORY) and a
 * user-safe message, and nothing else. Transport mapping (error code to HTTP
 * status) lives exclusively in the driving web adapter
 * ({@code GlobalExceptionHandler#statusFor}): the application and persistence
 * layers must not make transport decisions, so this type stays free of any
 * web-framework dependency and reusable by non-HTTP adapters.
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
