package com.enterpriseorders.backend.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlation ID filter (SRS §56).
 *
 * Reads the incoming X-Correlation-ID header or generates one and puts
 * it in the SLF4J MDC so logs, audit and outbox events can propagate it
 * through HTTP -> Spring -> Oracle -> Audit -> Outbox.
 *
 * Client supplied values are validated against a strict pattern; anything
 * else (CRLF, oversized, control chars) is discarded and replaced by a
 * generated UUID to prevent header injection and log forging.
 */
@Component
public class CorrelationFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || !VALID_CORRELATION_ID.matcher(correlationId).matches()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Propagate to MDC and response for downstream/audit traceability
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
