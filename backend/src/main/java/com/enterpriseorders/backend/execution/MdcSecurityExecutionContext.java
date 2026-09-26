package com.enterpriseorders.backend.execution;

import com.enterpriseorders.backend.web.filter.CorrelationFilter;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Default {@link ExecutionContext} backed by the SLF4J MDC (correlation
 * id published by CorrelationFilter) and the Spring Security
 * Authentication (actor published by HTTP Basic).
 *
 * Falls back to "APP_JAVA" when no authenticated principal is present
 * (scripts, actuator probes, unit tests) - the same default the PL/SQL
 * packages use for p_actor.
 */
@Component
public class MdcSecurityExecutionContext implements ExecutionContext {

    // Single ownership: the MDC key is defined by its producer
    // (CorrelationFilter.MDC_KEY); this bridge only reads it.
    private static final String DEFAULT_ACTOR = "APP_JAVA";

    @Override
    public String correlationId() {
        return MDC.get(CorrelationFilter.MDC_KEY);
    }

    @Override
    public String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            return DEFAULT_ACTOR;
        }
        return authentication.getName();
    }
}
