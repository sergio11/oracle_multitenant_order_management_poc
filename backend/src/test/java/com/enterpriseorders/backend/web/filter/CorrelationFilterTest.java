package com.enterpriseorders.backend.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private CorrelationFilter filter;

    @Test
    void shouldGenerateCorrelationIdWhenMissing() throws ServletException, IOException {
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Correlation-ID"), anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldUseExistingCorrelationId() throws ServletException, IOException {
        when(request.getHeader("X-Correlation-ID")).thenReturn("test-corr-123");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Correlation-ID", "test-corr-123");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldSetMdcDuringFilterChain() throws ServletException, IOException {
        when(request.getHeader("X-Correlation-ID")).thenReturn("mdc-test");

        doAnswer(invocation -> {
            assertEquals("mdc-test", MDC.get("correlationId"));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void shouldClearMdcAfterRequest() throws ServletException, IOException {
        when(request.getHeader("X-Correlation-ID")).thenReturn("mdc-test");

        filter.doFilterInternal(request, response, filterChain);

        assertNull(MDC.get("correlationId"));
    }
}
