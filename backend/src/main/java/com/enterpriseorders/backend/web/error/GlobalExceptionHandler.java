package com.enterpriseorders.backend.web.error;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.exception.DatabaseException;
import com.enterpriseorders.backend.exception.RefreshRateLimitException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.stream.Collectors;

/**
 * Global API error handling (SRS §54).
 *
 * Ensures a consistent error payload {code, message, correlationId}.
 * Internal Oracle errors (ORA-*, SQLException) are never exposed to the
 * consumer; they are logged and replaced with a safe technical code.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, WebRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        ApiError body = ApiError.of(ex.getCode(), ex.getMessage(), correlationId);
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(DatabaseException.class)
    public ResponseEntity<ApiError> handleDatabase(DatabaseException ex, WebRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        log.error("Database error (correlationId={}, sqlState={})",
                correlationId, ex.getSqlState(), ex);
        // Never expose internal ORA-* details (SRS §54)
        ApiError body = ApiError.of("DATABASE_ERROR", "Database operation failed", correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(DataAccessException ex, WebRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        log.error("Data access error (correlationId={})", correlationId, ex);
        ApiError body = ApiError.of("DATABASE_ERROR", "Database operation failed", correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ApiError body = ApiError.of("VALIDATION_ERROR", message, correlationId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedRequest(HttpMessageNotReadableException ex, WebRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        ApiError body = ApiError.of("MALFORMED_REQUEST", "INVALID_REQUEST_BODY", correlationId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(RefreshRateLimitException.class)
    public ResponseEntity<ApiError> handleRefreshRateLimit(RefreshRateLimitException ex, WebRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        ApiError body = ApiError.of("RATE_LIMIT_EXCEEDED", ex.getMessage(), correlationId);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, WebRequest request) {
        String correlationId = request.getHeader("X-Correlation-ID");
        log.error("Unhandled error (correlationId={})", correlationId, ex);
        ApiError body = ApiError.of("INTERNAL_ERROR", "An unexpected error occurred", correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
