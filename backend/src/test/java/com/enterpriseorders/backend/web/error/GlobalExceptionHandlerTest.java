package com.enterpriseorders.backend.web.error;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.exception.DatabaseException;
import com.enterpriseorders.backend.exception.RefreshRateLimitException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    private final WebRequest request = mock(WebRequest.class);

    @Test
    void shouldHandleBusinessException() {
        var ex = new BusinessException("ORDER_NOT_FOUND", "Order not found", 404);
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
        var ex = new BusinessException("TEST_ERROR", "test", 400);
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
}
