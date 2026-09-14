package com.enterpriseorders.backend.exception;

import java.sql.SQLException;
import java.util.Map;

/**
 * Centralized PL/SQL error code mapper (SRS §54).
 * Maps Oracle RAISE_APPLICATION_ERROR codes (ORA-20xxx) to
 * functional BusinessException or technical DatabaseException.
 */
public final class PlsqlErrorMapper {

    private static final Map<String, ErrorMapping> ORA_MAPPINGS = Map.ofEntries(
        Map.entry("ORA-20001", new ErrorMapping("TENANT_ALREADY_EXISTS", "Tenant code already exists", 409)),
        Map.entry("ORA-20002", new ErrorMapping("INVALID_TIER", "Invalid tier value", 400)),
        Map.entry("ORA-20003", new ErrorMapping("INVALID_ISOLATION_MODE", "Invalid isolation mode", 400)),
        Map.entry("ORA-20004", new ErrorMapping("TENANT_NOT_FOUND", "Tenant not found", 404)),
        Map.entry("ORA-20010", new ErrorMapping("INVALID_QUANTITY", "Reservation quantity must be positive", 400)),
        Map.entry("ORA-20011", new ErrorMapping("INSUFFICIENT_INVENTORY", "Insufficient inventory for product", 400)),
        Map.entry("ORA-20012", new ErrorMapping("INVENTORY_NOT_FOUND", "Inventory row not found", 404)),
        Map.entry("ORA-20013", new ErrorMapping("INVALID_RELEASE_QUANTITY", "Release quantity must be positive", 400)),
        Map.entry("ORA-20014", new ErrorMapping("INSUFFICIENT_RESERVED", "Reserved quantity insufficient for release", 400)),
        Map.entry("ORA-20020", new ErrorMapping("TENANT_SUSPENDED", "Tenant is not ACTIVE", 400)),
        Map.entry("ORA-20021", new ErrorMapping("CUSTOMER_TENANT_MISMATCH", "Customer does not belong to this tenant", 400)),
        Map.entry("ORA-20022", new ErrorMapping("PRODUCT_TENANT_MISMATCH", "Product does not belong to this tenant", 400)),
        Map.entry("ORA-20023", new ErrorMapping("ORDER_NOT_DRAFT", "Cannot add lines to a non-DRAFT order", 400)),
        Map.entry("ORA-20024", new ErrorMapping("ORDER_NOT_FOUND", "Order or warehouse not found", 404)),
        Map.entry("ORA-20025", new ErrorMapping("ORDER_NOT_CONFIRMABLE", "Order cannot be confirmed from current status", 400)),
        Map.entry("ORA-20026", new ErrorMapping("ORDER_NO_LINES", "Order has no lines - cannot confirm", 400)),
        Map.entry("ORA-20027", new ErrorMapping("ORDER_NOT_CANCELLABLE", "Order cannot be cancelled from current status", 400)),
        Map.entry("ORA-20028", new ErrorMapping("PAYMENT_STATE_INVALID", "Payment requires order CONFIRMED or later", 400)),
        Map.entry("ORA-20029", new ErrorMapping("DUPLICATE_PAYMENT_REF", "Duplicate payment reference", 409)),
        Map.entry("ORA-20030", new ErrorMapping("INVENTORY_NOT_FOUND_TEST", "Inventory record not found for test stock update", 404))
    );

    private PlsqlErrorMapper() {
    }

    /**
     * Maps a SQLException containing an ORA-20xxx code to the appropriate
     * RuntimeException. Returns BusinessException for known business errors,
     * DatabaseException for unknown Oracle errors.
     */
    public static RuntimeException map(SQLException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();

        for (var entry : ORA_MAPPINGS.entrySet()) {
            if (message.contains(entry.getKey())) {
                ErrorMapping mapping = entry.getValue();
                return new BusinessException(mapping.code(), mapping.message(), mapping.httpStatus());
            }
        }

        // Unknown ORA code -> technical DatabaseException (HTTP 500)
        return new DatabaseException("DATABASE_ERROR", "Database operation failed", ex, ex.getSQLState());
    }

    private record ErrorMapping(String code, String message, int httpStatus) {
    }
}
