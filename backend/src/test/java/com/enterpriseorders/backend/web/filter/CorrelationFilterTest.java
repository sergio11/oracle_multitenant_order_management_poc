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

    @Test
    void shouldGenerateNewIdWhenHeaderContainsCrlf() throws ServletException, IOException {
        String malicious = "corr\r\nSet-Cookie: evil=1";
        when(request.getHeader("X-Correlation-ID")).thenReturn(malicious);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Correlation-ID"), argThat(v -> !v.equals(malicious)));
        verify(response).setHeader(eq("X-Correlation-ID"), argThat(v -> v.matches("[0-9a-fA-F-]{36}")));
    }

    @Test
    void shouldGenerateNewIdWhenHeaderTooLong() throws ServletException, IOException {
        String oversized = "a".repeat(65);
        when(request.getHeader("X-Correlation-ID")).thenReturn(oversized);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Correlation-ID"), argThat(v -> !v.equals(oversized)));
        verify(response).setHeader(eq("X-Correlation-ID"), argThat(v -> v.matches("[0-9a-fA-F-]{36}")));
    }

    @Test
    void shouldGenerateNewIdWhenHeaderBlank() throws ServletException, IOException {
        when(request.getHeader("X-Correlation-ID")).thenReturn("   ");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Correlation-ID"), argThat(v -> v.matches("[0-9a-fA-F-]{36}")));
    }

    @Test
    void shouldGenerateNewIdWhenHeaderHasInvalidCharacters() throws ServletException, IOException {
        String invalid = "corr id<script>alert(1)</script>";
        when(request.getHeader("X-Correlation-ID")).thenReturn(invalid);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Correlation-ID"), argThat(v -> !v.equals(invalid)));
        verify(response).setHeader(eq("X-Correlation-ID"), argThat(v -> v.matches("[0-9a-fA-F-]{36}")));
    }

    @Test
    void shouldAcceptHeaderWithAllowedSpecialCharsAndMaxLength() throws ServletException, IOException {
        String valid = "a".repeat(60) + "._-";
        when(request.getHeader("X-Correlation-ID")).thenReturn(valid);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Correlation-ID", valid);
    }

    @Test
    void shouldPutGeneratedIdInMdcWhenHeaderInvalid() throws ServletException, IOException {
        String invalid = "bad\r\nheader";
        when(request.getHeader("X-Correlation-ID")).thenReturn(invalid);

        doAnswer(invocation -> {
            String mdcValue = MDC.get("correlationId");
            assertNotNull(mdcValue);
            assertNotEquals(invalid, mdcValue);
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);
    }
}
