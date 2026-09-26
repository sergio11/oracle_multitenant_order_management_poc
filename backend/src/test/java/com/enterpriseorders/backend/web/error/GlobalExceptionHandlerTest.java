package com.enterpriseorders.backend.web.error;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.exception.DatabaseException;
import com.enterpriseorders.backend.exception.RefreshRateLimitException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    private final WebRequest request = mock(WebRequest.class);

    @BeforeEach
    void clearMdc() {
        MDC.clear();
    }

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    void shouldHandleBusinessException() {
        var ex = new BusinessException("ORDER_NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND);
        ResponseEntity<ApiError> response = exceptionHandler.handleBusiness(ex, request);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("ORDER_NOT_FOUND", response.getBody().code());
        assertEquals("Order not found", response.getBody().message());
    }

    @Test
    void shouldHandleDatabaseException() {
        var ex = new DatabaseException("DATABASE_ERROR", "Connection failed", null);
        ResponseEntity<ApiError> response = exceptionHandler.handleDatabase(ex, request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("DATABASE_ERROR", response.getBody().code());
    }

    @Test
    void shouldHandleRefreshRateLimitException() {
        var ex = new RefreshRateLimitException("Rate limit exceeded");
        ResponseEntity<ApiError> response = exceptionHandler.handleRefreshRateLimit(ex, request);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("RATE_LIMIT_EXCEEDED", response.getBody().code());
        assertEquals("Rate limit exceeded", response.getBody().message());
    }

    @Test
    void shouldHandleValidationException() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("order", "customerId", "must not be null")
        ));
        var ex = new MethodArgumentNotValidException(null, bindingResult);
        ResponseEntity<ApiError> response = exceptionHandler.handleValidation(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
        assertTrue(response.getBody().message().contains("customerId"));
    }

    @Test
    void shouldHandleGenericException() {
        var ex = new RuntimeException("Something unexpected");
        ResponseEntity<ApiError> response = exceptionHandler.handleGeneric(ex, request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
    }

    @Test
    void shouldIncludeCorrelationIdWhenPresent() {
        when(request.getHeader("X-Correlation-ID")).thenReturn("corr-123");
        var ex = new BusinessException("TEST_ERROR", "test", HttpStatus.BAD_REQUEST);
        ResponseEntity<ApiError> response = exceptionHandler.handleBusiness(ex, request);
        assertEquals("corr-123", response.getBody().correlationId());
    }

    @Test
    void shouldHandleMultipleFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("order", "customerId", "must not be null"),
                new FieldError("order", "currency", "must not be blank")
        ));
        var ex = new MethodArgumentNotValidException(null, bindingResult);
        ResponseEntity<ApiError> response = exceptionHandler.handleValidation(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String msg = response.getBody().message();
        assertTrue(msg.contains("customerId"));
        assertTrue(msg.contains("currency"));
    }

    @Test
    void shouldHandleDataAccessException() {
        DataAccessException ex = new DataAccessResourceFailureException("Connection lost");
        ResponseEntity<ApiError> response = exceptionHandler.handleDataAccess(ex, request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("DATABASE_ERROR", response.getBody().code());
    }

    @Test
    void shouldHandleIllegalStateExceptionAsGeneric() {
        IllegalStateException ex = new IllegalStateException("Tenant context not set");
        ResponseEntity<ApiError> response = exceptionHandler.handleGeneric(ex, request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
    }

    @Test
    void shouldIncludeCorrelationIdInDatabaseError() {
        when(request.getHeader("X-Correlation-ID")).thenReturn("db-corr-456");
        var ex = new DatabaseException("DATABASE_ERROR", "Timeout", new RuntimeException(), "08001");
        ResponseEntity<ApiError> response = exceptionHandler.handleDatabase(ex, request);
        assertEquals("db-corr-456", response.getBody().correlationId());
    }

    @Test
    void shouldFallBackToMdcCorrelationIdWhenHeaderMissing() {
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);
        MDC.put("correlationId", "mdc-corr-789");
        var ex = new BusinessException("TEST", "test", HttpStatus.BAD_REQUEST);
        ResponseEntity<ApiError> response = exceptionHandler.handleBusiness(ex, request);
        assertEquals("mdc-corr-789", response.getBody().correlationId());
    }

    @Test
    void shouldReturnNullCorrelationIdWhenHeaderMissing() {
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);
        var ex = new BusinessException("TEST", "test", HttpStatus.BAD_REQUEST);
        ResponseEntity<ApiError> response = exceptionHandler.handleBusiness(ex, request);
        assertNull(response.getBody().correlationId());
    }

    @Test
    void shouldPreferMdcCorrelationIdOverHeader() {
        when(request.getHeader("X-Correlation-ID")).thenReturn("header-corr");
        MDC.put("correlationId", "mdc-corr");
        var ex = new BusinessException("TEST", "test", HttpStatus.BAD_REQUEST);
        ResponseEntity<ApiError> response = exceptionHandler.handleBusiness(ex, request);
        assertEquals("mdc-corr", response.getBody().correlationId());
    }

    @Test
    void shouldHandleMethodArgumentTypeMismatch() {
        var ex = new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, null);
        ResponseEntity<ApiError> response = exceptionHandler.handleTypeMismatch(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_PARAMETER_TYPE", response.getBody().code());
        assertTrue(response.getBody().message().contains("id"));
    }

    @Test
    void shouldHandleMethodNotSupported() {
        var ex = new HttpRequestMethodNotSupportedException("PUT");
        ResponseEntity<ApiError> response = exceptionHandler.handleMethodNotSupported(ex, request);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals("METHOD_NOT_ALLOWED", response.getBody().code());
        assertTrue(response.getBody().message().contains("PUT"));
    }

    @Test
    void shouldHandleUnsupportedMediaType() {
        var ex = new HttpMediaTypeNotSupportedException("application/xml");
        ResponseEntity<ApiError> response = exceptionHandler.handleUnsupportedMediaType(ex, request);
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals("UNSUPPORTED_MEDIA_TYPE", response.getBody().code());
    }

    @Test
    void shouldHandleNoResourceFound() {
        var ex = new NoResourceFoundException(HttpMethod.GET, "/api/v1/unknown");
        ResponseEntity<ApiError> response = exceptionHandler.handleNoResourceFound(ex, request);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("NOT_FOUND", response.getBody().code());
    }

    @Test
    void shouldHandleMissingServletRequestParameter() {
        var ex = new MissingServletRequestParameterException("warehouseId", "String");
        ResponseEntity<ApiError> response = exceptionHandler.handleMissingParameter(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MISSING_PARAMETER", response.getBody().code());
        assertTrue(response.getBody().message().contains("warehouseId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldHandleConstraintViolationException() {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("placeOrder.quantity");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be positive");
        var ex = new ConstraintViolationException(Set.of(violation));
        ResponseEntity<ApiError> response = exceptionHandler.handleConstraintViolation(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("CONSTRAINT_VIOLATION", response.getBody().code());
        assertTrue(response.getBody().message().contains("placeOrder.quantity"));
    }

    @Test
    void shouldHandleMalformedRequest() {
        var ex = new HttpMessageNotReadableException("JSON parse failed");
        ResponseEntity<ApiError> response = exceptionHandler.handleMalformedRequest(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("MALFORMED_REQUEST", response.getBody().code());
    }

    @Test
    void shouldMapDataIntegrityViolationWithOra01401ToValidationError() {
        var ex = new DataIntegrityViolationException("insert failed",
                new RuntimeException("ORA-01401: inserted value too large for column"));
        ResponseEntity<ApiError> response = exceptionHandler.handleDataIntegrity(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
    }

    @Test
    void shouldMapDataIntegrityViolationWithOra06502ToValidationError() {
        var ex = new DataIntegrityViolationException("update failed",
                new RuntimeException("ORA-06502: PL/SQL: numeric or value error"));
        ResponseEntity<ApiError> response = exceptionHandler.handleDataIntegrity(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
    }

    @Test
    void shouldMapOtherDataIntegrityViolationToInternalServerError() {
        var ex = new DataIntegrityViolationException("constraint violated",
                new RuntimeException("ORA-02292: integrity constraint violated"));
        ResponseEntity<ApiError> response = exceptionHandler.handleDataIntegrity(ex, request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("DATABASE_ERROR", response.getBody().code());
    }
}
