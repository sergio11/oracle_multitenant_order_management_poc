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

    /**
     * Mirrors the PL/SQL transition matrix from {@code pkg_order_management}.
     * The authoritative state machine lives in PL/SQL; this method documents
     * the same rules for the Java layer.
     */
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case DRAFT -> next == PENDING;
            case PENDING -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == ALLOCATED || next == PAID || next == CANCELLED;
            case ALLOCATED -> next == PAID || next == SHIPPED || next == CANCELLED;
            case PAID -> next == SHIPPED || next == COMPLETED || next == CANCELLED;
            case SHIPPED -> next == COMPLETED;
            case COMPLETED -> false;
            case CANCELLED -> false;
        };
    }

    public static OrderStatus fromString(String status) {
        try {
            return valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid order status: " + status);
        }
    }
}
