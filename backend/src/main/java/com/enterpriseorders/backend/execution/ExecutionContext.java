package com.enterpriseorders.backend.execution;

/**
 * Application-layer access to request-scoped audit data.
 *
 * Keeps the application layer free of MDC and Spring Security lookups
 * while still providing the actor and correlation id that the PL/SQL
 * packages record on every mutation.
 */
public interface ExecutionContext {

    String correlationId();

    String actor();
}
