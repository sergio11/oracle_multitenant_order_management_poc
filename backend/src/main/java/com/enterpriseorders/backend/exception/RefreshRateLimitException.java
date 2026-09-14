package com.enterpriseorders.backend.exception;

public class RefreshRateLimitException extends RuntimeException {
    public RefreshRateLimitException(String message) {
        super(message);
    }
}
