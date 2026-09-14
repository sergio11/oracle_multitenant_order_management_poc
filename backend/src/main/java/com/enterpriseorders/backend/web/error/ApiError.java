package com.enterpriseorders.backend.web.error;

/**
 * Consistent API error payload (SRS §54).
 *
 * Example:
 *   {
 *     "code": "INSUFFICIENT_INVENTORY",
 *     "message": "Insufficient inventory for product",
 *     "correlationId": "..."
 *   }
 *
 * Internal Oracle errors (ORA-*) are never exposed to the consumer.
 */
public record ApiError(
        String code,
        String message,
        String correlationId
) {
    public static ApiError of(String code, String message, String correlationId) {
        return new ApiError(code, message, correlationId);
    }
}
