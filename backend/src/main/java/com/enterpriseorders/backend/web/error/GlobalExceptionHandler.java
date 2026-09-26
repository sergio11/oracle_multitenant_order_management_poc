package com.enterpriseorders.backend.web.error;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.exception.DatabaseException;
import com.enterpriseorders.backend.exception.RefreshRateLimitException;
import com.enterpriseorders.backend.web.filter.CorrelationFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global API error handling (SRS §54).
 *
 * Ensures a consistent error payload {code, message, correlationId}.
 * Internal Oracle errors (ORA-*, SQLException) are never exposed to the
 * consumer; they are logged and replaced with a safe technical code.
 *
 * Spring MVC routing/argument exceptions are mapped to their proper 4xx
 * status; {@code Exception.class} remains the last-resort 500 handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Single error-code to HTTP-status table for the whole API (Protected
     * Variations: transport decisions live only in this driving adapter, so
     * the application and persistence layers never import HTTP types).
     * Unknown codes degrade to 500 rather than leaking a misleading 4xx.
     *
     * <p>Deliberate split: {@code TENANT_NOT_FOUND} (404, the record does not
     * exist) vs {@code TENANT_ACCESS_DENIED} (401, anti-enumeration: the
     * resolution path refuses to reveal whether a code exists).
     */
    private static final Map<String, HttpStatus> CODE_STATUS = Map.ofEntries(
            Map.entry("TENANT_ALREADY_EXISTS", HttpStatus.CONFLICT),
            Map.entry("INVALID_TIER", HttpStatus.BAD_REQUEST),
            Map.entry("INVALID_ISOLATION_MODE", HttpStatus.BAD_REQUEST),
            Map.entry("TENANT_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("TENANT_ACCESS_DENIED", HttpStatus.UNAUTHORIZED),
            Map.entry("INVALID_QUANTITY", HttpStatus.BAD_REQUEST),
            Map.entry("INSUFFICIENT_INVENTORY", HttpStatus.BAD_REQUEST),
            Map.entry("INVENTORY_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("INVENTORY_NOT_FOUND_TEST", HttpStatus.NOT_FOUND),
            Map.entry("INVALID_RELEASE_QUANTITY", HttpStatus.BAD_REQUEST),
            Map.entry("INSUFFICIENT_RESERVED", HttpStatus.BAD_REQUEST),
            Map.entry("TENANT_SUSPENDED", HttpStatus.BAD_REQUEST),
            Map.entry("CUSTOMER_TENANT_MISMATCH", HttpStatus.BAD_REQUEST),
            Map.entry("PRODUCT_TENANT_MISMATCH", HttpStatus.BAD_REQUEST),
            Map.entry("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("ORDER_NOT_DRAFT", HttpStatus.BAD_REQUEST),
            Map.entry("ORDER_NOT_CONFIRMABLE", HttpStatus.BAD_REQUEST),
            Map.entry("ORDER_NO_LINES", HttpStatus.BAD_REQUEST),
            Map.entry("ORDER_NOT_CANCELLABLE", HttpStatus.BAD_REQUEST),
            Map.entry("ORDER_SHIPPED_NOT_CANCELLABLE", HttpStatus.CONFLICT),
            Map.entry("CANCEL_REASON_REQUIRED", HttpStatus.BAD_REQUEST),
            Map.entry("PAYMENT_STATE_INVALID", HttpStatus.BAD_REQUEST),
            Map.entry("DUPLICATE_PAYMENT_REF", HttpStatus.CONFLICT),
            Map.entry("ORDER_ALREADY_PAID", HttpStatus.CONFLICT),
            Map.entry("PAYMENT_AMOUNT_INVALID", HttpStatus.BAD_REQUEST),
            Map.entry("PAYMENT_CURRENCY_MISMATCH", HttpStatus.BAD_REQUEST),
            Map.entry("PAYMENT_AMOUNT_MISMATCH", HttpStatus.BAD_REQUEST),
            Map.entry("STATUS_TRANSITION_CONFLICT", HttpStatus.CONFLICT),
            Map.entry("TENANT_CONTEXT_MISSING", HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry("INVALID_IDEMPOTENCY_KEY", HttpStatus.BAD_REQUEST),
            Map.entry("IDEMPOTENCY_RACE_CONFLICT", HttpStatus.CONFLICT),
            Map.entry("IDEMPOTENCY_IN_PROGRESS", HttpStatus.CONFLICT),
            Map.entry("IDEMPOTENCY_PAYLOAD_MISSING", HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry("IDEMPOTENCY_KEY_CONFLICT", HttpStatus.CONFLICT),
            Map.entry("INVALID_LIMIT", HttpStatus.BAD_REQUEST));

    /**
     * Resolves the HTTP status for a functional error code. Public so that
     * other driving adapters (e.g. filters writing their own error bodies)
     * share this single table instead of duplicating it.
     */
    public static HttpStatus statusFor(String code) {
        return CODE_STATUS.getOrDefault(code, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        ApiError body = ApiError.of(ex.getCode(), ex.getMessage(), correlationId);
        return ResponseEntity.status(statusFor(ex.getCode())).body(body);
    }

    @ExceptionHandler(DatabaseException.class)
    public ResponseEntity<ApiError> handleDatabase(DatabaseException ex, WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.error("Database error (correlationId={}, sqlState={})",
                correlationId, ex.getSqlState(), ex);
        // Never expose internal ORA-* details (SRS §54)
        ApiError body = ApiError.of("DATABASE_ERROR", "Database operation failed", correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        if (isClientSideDataError(ex)) {
            log.warn("Data integrity violation caused by invalid input (correlationId={})", correlationId, ex);
            ApiError body = ApiError.of("VALIDATION_ERROR", "Invalid data for the given operation", correlationId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        }
        log.error("Data integrity violation (correlationId={})", correlationId, ex);
        ApiError body = ApiError.of("DATABASE_ERROR", "Database operation failed", correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(DataAccessException ex, WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.error("Data access error (correlationId={})", correlationId, ex);
        ApiError body = ApiError.of("DATABASE_ERROR", "Database operation failed", correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        ApiError body = ApiError.of("VALIDATION_ERROR", message, correlationId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedRequest(HttpMessageNotReadableException ex, WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        ApiError body = ApiError.of("MALFORMED_REQUEST", "INVALID_REQUEST_BODY", correlationId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        String type = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "valid type";
        String message = "Parameter '" + ex.getName() + "' must be of type " + type;
        ApiError body = ApiError.of("INVALID_PARAMETER_TYPE", message, correlationId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                           WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        String message = "Missing required parameter '" + ex.getParameterName() + "'";
        ApiError body = ApiError.of("MISSING_PARAMETER", message, correlationId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        String message = ex.getConstraintViolations().stream()
                .map(cv -> violationPath(cv) + ": " + cv.getMessage())
                .collect(Collectors.joining(", "));
        ApiError body = ApiError.of("CONSTRAINT_VIOLATION", message, correlationId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        String message = "Method " + ex.getMethod() + " not supported";
        ApiError body = ApiError.of("METHOD_NOT_ALLOWED", message, correlationId);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                               WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        ApiError body = ApiError.of("UNSUPPORTED_MEDIA_TYPE", "Content type not supported", correlationId);
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex, WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        ApiError body = ApiError.of("NOT_FOUND", "Resource not found", correlationId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(RefreshRateLimitException.class)
    public ResponseEntity<ApiError> handleRefreshRateLimit(RefreshRateLimitException ex, WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        ApiError body = ApiError.of("RATE_LIMIT_EXCEEDED", ex.getMessage(), correlationId);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, WebRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.error("Unhandled/unexpected error (correlationId={})", correlationId, ex);
        ApiError body = ApiError.of("INTERNAL_ERROR", "An unexpected error occurred", correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Correlation id for the error body: MDC first (set by CorrelationFilter,
     * always present during a real request), then the raw request header,
     * then null (e.g. direct unit invocation without either).
     */
    private String resolveCorrelationId(WebRequest request) {
        String fromHeader = request.getHeader(CorrelationFilter.CORRELATION_HEADER);
        String fromMdc = MDC.get(CorrelationFilter.MDC_KEY);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        return (fromHeader == null || fromHeader.isBlank()) ? null : fromHeader;
    }

    /** ORA-01401 / ORA-06502 are caused by invalid client data, not by a server fault. */
    private boolean isClientSideDataError(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains("ORA-01401") || message.contains("ORA-06502"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String violationPath(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
    }
}
