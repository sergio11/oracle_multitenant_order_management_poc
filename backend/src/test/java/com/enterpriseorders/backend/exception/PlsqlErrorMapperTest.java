package com.enterpriseorders.backend.exception;

import com.enterpriseorders.backend.web.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class PlsqlErrorMapperTest {

    /** Realistic Oracle exception: vendor code populated, as ojdbc sets it. */
    private static SQLException ora(int vendorCode, String message) {
        return new SQLException("ORA-" + vendorCode + ": " + message, "72000", vendorCode);
    }

    private static void assertBusinessCode(RuntimeException result, String code, int httpStatus) {
        assertInstanceOf(BusinessException.class, result);
        BusinessException be = (BusinessException) result;
        assertEquals(code, be.getCode());
        assertEquals(httpStatus, GlobalExceptionHandler.statusFor(be.getCode()).value());
    }

    @Test
    void shouldMapOra20001ToTenantAlreadyExists() {
        assertBusinessCode(PlsqlErrorMapper.map(ora(20001, "Tenant already exists")),
                "TENANT_ALREADY_EXISTS", 409);
    }

    @Test
    void shouldMapOra20011ToInsufficientInventory() {
        assertBusinessCode(PlsqlErrorMapper.map(ora(20011, "Insufficient inventory")),
                "INSUFFICIENT_INVENTORY", 400);
    }

    @Test
    void shouldMapOra20025ToOrderNotConfirmable() {
        assertBusinessCode(PlsqlErrorMapper.map(ora(20025, "Order not confirmable")),
                "ORDER_NOT_CONFIRMABLE", 400);
    }

    @Test
    void shouldMapByVendorCodeEvenWhenMessageHidesIt() {
        // Wrapped/normalized messages may not contain "ORA-2xxxx" at all;
        // the vendor-code lookup must still resolve the business error.
        SQLException ex = new SQLException("order rejected by stock policy", "72000", 20011);
        assertBusinessCode(PlsqlErrorMapper.map(ex), "INSUFFICIENT_INVENTORY", 400);
    }

    @Test
    void shouldFallBackToMessageMatchingWithoutVendorCode() {
        // Drivers that leave getErrorCode() == 0 still resolve via message.
        SQLException ex = new SQLException("ORA-20004: Tenant not found", "ORA-20004");
        assertBusinessCode(PlsqlErrorMapper.map(ex), "TENANT_NOT_FOUND", 404);
    }

    @Test
    void shouldReturnDatabaseExceptionForUnknownOraCode() {
        SQLException ex = new SQLException("ORA-99999: Unknown error", "ORA-99999");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(DatabaseException.class, result);
        assertEquals("DATABASE_ERROR", ((DatabaseException) result).getCode());
    }

    @Test
    void shouldReturnDatabaseExceptionForNonOraException() {
        SQLException ex = new SQLException("Connection refused", "08001");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(DatabaseException.class, result);
        assertEquals("DATABASE_ERROR", ((DatabaseException) result).getCode());
    }

    @Test
    void shouldMapOra20004ToTenantNotFound() {
        assertBusinessCode(PlsqlErrorMapper.map(ora(20004, "Tenant not found")),
                "TENANT_NOT_FOUND", 404);
    }

    @Test
    void shouldMapOra20029ToDuplicatePaymentRef() {
        assertBusinessCode(PlsqlErrorMapper.map(ora(20029, "Duplicate payment reference")),
                "DUPLICATE_PAYMENT_REF", 409);
    }

    @Test
    void shouldPreserveOriginalExceptionAsCause() {
        SQLException ex = new SQLException("ORA-99999: Unknown", "ORA-99999");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertSame(ex, result.getCause());
    }

    @Test
    void shouldHandleNullMessageInException() {
        SQLException ex = new SQLException((String) null, "08001");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(DatabaseException.class, result);
    }

    @Test
    void shouldMapOra20031ToOrderAlreadyPaid() {
        assertBusinessCode(PlsqlErrorMapper.map(ora(20031, "Order already paid")),
                "ORDER_ALREADY_PAID", 409);
    }

    @Test
    void shouldMapOra20032ToPaymentAmountInvalid() {
        assertBusinessCode(PlsqlErrorMapper.map(ora(20032, "Payment amount must be positive")),
                "PAYMENT_AMOUNT_INVALID", 400);
    }

    @Test
    void shouldMapOra20033ToPaymentCurrencyMismatch() {
        assertBusinessCode(PlsqlErrorMapper.map(ora(20033, "Payment currency mismatch")),
                "PAYMENT_CURRENCY_MISMATCH", 400);
    }

    @Test
    void shouldMapOra20034ToShippedNotCancellable() {
        assertBusinessCode(PlsqlErrorMapper.map(ora(20034, "Cannot cancel a shipped order")),
                "ORDER_SHIPPED_NOT_CANCELLABLE", 409);
    }

    @Test
    void shouldMapOra20035ToPaymentAmountMismatch() {
        assertBusinessCode(PlsqlErrorMapper.map(ora(20035, "Payment amount does not match order total")),
                "PAYMENT_AMOUNT_MISMATCH", 400);
    }

    @Test
    void shouldMapOra20036ToInvalidQuantity() {
        assertBusinessCode(PlsqlErrorMapper.map(ora(20036, "Quantity must be greater than zero")),
                "INVALID_QUANTITY", 400);
    }

    @Test
    void shouldMapOra20038ToStatusTransitionConflict() {
        assertBusinessCode(PlsqlErrorMapper.map(ora(20038, "Status transition failed")),
                "STATUS_TRANSITION_CONFLICT", 409);
    }
}
