package com.enterpriseorders.backend.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Request payload for registering a payment (SRS §53, FR-011).
 *
 * paymentReference acts as a natural idempotency guard: the PL/SQL
 * package rejects duplicates via the unique (tenant_id, payment_reference)
 * constraint (FR-014).
 */
public record PaymentRequest(
        @NotBlank String paymentReference,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotBlank @Pattern(regexp = "CARD|BANK_TRANSFER|WALLET|CASH") String paymentMethod
) {
}