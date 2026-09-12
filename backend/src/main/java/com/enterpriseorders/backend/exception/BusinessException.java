package com.enterpriseorders.backend.exception;

/**
 * Functional business exception (SRS §54).
 *
 * Carries a functional error code (e.g. INSUFFICIENT_INVENTORY) and a
 * user-safe message. Maps to a consistent ApiError response, never
 * exposing internal Oracle ORA-* details.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public BusinessException(String code, String message) {
        this(code, message, 400);
    }

    public BusinessException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}