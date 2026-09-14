package com.enterpriseorders.backend.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for creating an order (SRS §53, FR-007).
 *
 * customerId must belong to the same tenant (BR-002), enforced by the
 * PL/SQL package pkg_order_management.create_order.
 */
public record CreateOrderRequest(
        @NotNull Long customerId,
        @NotBlank String currency
) {
}
