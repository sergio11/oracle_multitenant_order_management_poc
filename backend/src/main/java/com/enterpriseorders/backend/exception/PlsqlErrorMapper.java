package com.enterpriseorders.backend.exception;

import java.sql.SQLException;
import java.util.Map;

/**
 * Centralized PL/SQL error code mapper (SRS §54).
 * Maps Oracle RAISE_APPLICATION_ERROR codes (ORA-20xxx) to
 * functional BusinessException or technical DatabaseException.
 *
 * <p>Lookup is by {@link SQLException#getErrorCode()} first (stable,
 * locale-independent); message-substring matching remains only as a fallback
 * for drivers that do not populate the vendor code.
 */
public final class PlsqlErrorMapper {

    private static final Map<String, ErrorMapping> ORA_MAPPINGS = Map.ofEntries(
        Map.entry("ORA-20001", new ErrorMapping("TENANT_ALREADY_EXISTS", "Tenant code already exists")),
        Map.entry("ORA-20002", new ErrorMapping("INVALID_TIER", "Invalid tier value")),
        Map.entry("ORA-20003", new ErrorMapping("INVALID_ISOLATION_MODE", "Invalid isolation mode")),
        Map.entry("ORA-20004", new ErrorMapping("TENANT_NOT_FOUND", "Tenant not found")),
        Map.entry("ORA-20005", new ErrorMapping("TENANT_CONTEXT_REJECTED", "Tenant context rejected: unknown or inactive tenant")),
        Map.entry("ORA-20010", new ErrorMapping("INVALID_QUANTITY", "Reservation quantity must be positive")),
        Map.entry("ORA-20011", new ErrorMapping("INSUFFICIENT_INVENTORY", "Insufficient inventory for product")),
        Map.entry("ORA-20012", new ErrorMapping("INVENTORY_NOT_FOUND", "Inventory row not found")),
        Map.entry("ORA-20013", new ErrorMapping("INVALID_RELEASE_QUANTITY", "Release quantity must be positive")),
        Map.entry("ORA-20014", new ErrorMapping("INSUFFICIENT_RESERVED", "Reserved quantity insufficient for release")),
        Map.entry("ORA-20020", new ErrorMapping("TENANT_SUSPENDED", "Tenant is not ACTIVE")),
        Map.entry("ORA-20021", new ErrorMapping("CUSTOMER_TENANT_MISMATCH", "Customer does not belong to this tenant")),
        Map.entry("ORA-20022", new ErrorMapping("PRODUCT_TENANT_MISMATCH", "Product does not belong to this tenant")),
        Map.entry("ORA-20023", new ErrorMapping("ORDER_NOT_DRAFT", "Cannot add lines to a non-DRAFT/PENDING order")),
        Map.entry("ORA-20024", new ErrorMapping("ORDER_NOT_FOUND", "Order or warehouse not found")),
        Map.entry("ORA-20025", new ErrorMapping("ORDER_NOT_CONFIRMABLE", "Order cannot be confirmed from current status")),
        Map.entry("ORA-20026", new ErrorMapping("ORDER_NO_LINES", "Order has no lines - cannot confirm")),
        Map.entry("ORA-20027", new ErrorMapping("ORDER_NOT_CANCELLABLE", "Order cannot be cancelled from current status")),
        Map.entry("ORA-20028", new ErrorMapping("PAYMENT_STATE_INVALID", "Payment requires order CONFIRMED or later")),
        Map.entry("ORA-20029", new ErrorMapping("DUPLICATE_PAYMENT_REF", "Duplicate payment reference")),
        Map.entry("ORA-20030", new ErrorMapping("INVENTORY_NOT_FOUND_TEST", "Inventory record not found for test stock update")),
        Map.entry("ORA-20031", new ErrorMapping("ORDER_ALREADY_PAID", "Order already paid; duplicate payment rejected")),
        Map.entry("ORA-20032", new ErrorMapping("PAYMENT_AMOUNT_INVALID", "Payment amount must be positive")),
        Map.entry("ORA-20033", new ErrorMapping("PAYMENT_CURRENCY_MISMATCH", "Payment currency does not match order currency")),
        Map.entry("ORA-20034", new ErrorMapping("ORDER_SHIPPED_NOT_CANCELLABLE", "Cannot cancel a shipped order")),
        Map.entry("ORA-20035", new ErrorMapping("PAYMENT_AMOUNT_MISMATCH", "Payment amount does not match order total")),
        Map.entry("ORA-20036", new ErrorMapping("INVALID_QUANTITY", "Quantity must be greater than zero")),
        Map.entry("ORA-20037", new ErrorMapping("NO_ACTIVE_WAREHOUSE", "No ACTIVE warehouse configured for this tenant")),
        Map.entry("ORA-20038", new ErrorMapping("STATUS_TRANSITION_CONFLICT", "Status transition failed: order not found or modified concurrently")),
        Map.entry("ORA-20039", new ErrorMapping("INVALID_RELAY_BATCH_SIZE", "Outbox relay batch size must be positive")),
        Map.entry("ORA-20040", new ErrorMapping("INVALID_RETENTION_DAYS", "Idempotency retention must be at least 1 day")),
        // Infrastructure-level contention, mapped so a saturated row lock
        // surfaces as an actionable 409 instead of a generic 500.
        Map.entry("ORA-00060", new ErrorMapping("CONCURRENT_MODIFICATION", "Deadlock detected; retry the operation")),
        Map.entry("ORA-30006", new ErrorMapping("LOCK_TIMEOUT", "Timed out waiting for a row lock; retry the operation"))
    );

    private PlsqlErrorMapper() {
    }

    /**
     * Maps a SQLException containing an ORA-20xxx code to the appropriate
     * RuntimeException. Returns BusinessException for known business errors,
     * DatabaseException for unknown Oracle errors.
     */
    public static RuntimeException map(SQLException ex) {
        int vendorCode = ex.getErrorCode();
        if (vendorCode == 60 || vendorCode == 30006) {
            // Deadlock / lock timeout: vendor codes outside the 20xxx band,
            // but still functional outcomes the caller can retry.
            ErrorMapping mapping = ORA_MAPPINGS.get("ORA-" + String.format("%05d", vendorCode));
            if (mapping != null) {
                return new BusinessException(mapping.code(), mapping.message());
            }
        }
        if (vendorCode >= 20000 && vendorCode <= 20999) {
            ErrorMapping mapping = ORA_MAPPINGS.get("ORA-" + vendorCode);
            if (mapping != null) {
                return new BusinessException(mapping.code(), mapping.message());
            }
        }

        String message = ex.getMessage() == null ? "" : ex.getMessage();
        for (var entry : ORA_MAPPINGS.entrySet()) {
            if (message.contains(entry.getKey())) {
                ErrorMapping mapping = entry.getValue();
                return new BusinessException(mapping.code(), mapping.message());
            }
        }

        // Unknown ORA code -> technical DatabaseException (HTTP 500)
        return new DatabaseException("DATABASE_ERROR", "Database operation failed", ex, ex.getSQLState());
    }

    private record ErrorMapping(String code, String message) {
    }
}
