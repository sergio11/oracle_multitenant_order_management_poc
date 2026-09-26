package com.enterpriseorders.backend.common.domain;

/**
 * Order lifecycle states.
 *
 * <p>The authoritative state machine lives in PL/SQL
 * ({@code pkg_order_management}); this enum mirrors the same rules for
 * the Java layer and documents the state machine. Transitions are
 * applied atomically in the database — this enum is not the authority.</p>
 */
public enum OrderStatus {
    DRAFT,
    PENDING,
    CONFIRMED,
    ALLOCATED,
    PAID,
    SHIPPED,
    COMPLETED,
    CANCELLED;

    public static OrderStatus fromString(String status) {
        if (status == null) {
            throw new IllegalArgumentException("Invalid order status: null");
        }
        try {
            return valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid order status: " + status, e);
        }
    }
}
