package com.enterpriseorders.backend.common.domain;

public enum OrderStatus {
    DRAFT,
    PENDING,
    CONFIRMED,
    ALLOCATED,
    PAID,
    SHIPPED,
    COMPLETED,
    CANCELLED;

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
