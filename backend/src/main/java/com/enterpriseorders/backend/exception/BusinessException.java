package com.enterpriseorders.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Functional business exception (SRS §54).
 *
 * Carries a functional error code (e.g. INSUFFICIENT_INVENTORY) and a
 * user-safe message. Maps to a consistent ApiError response, never
 * exposing internal Oracle ORA-* details.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /**
     * Integer-status constructor kept for callers outside the web layer.
     *
     * @deprecated prefer {@link #BusinessException(String, String, HttpStatus)}.
     */
    @Deprecated
    public BusinessException(String code, String message, int httpStatus) {
        this(code, message, resolve(httpStatus));
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus.value();
    }

    private static HttpStatus resolve(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
