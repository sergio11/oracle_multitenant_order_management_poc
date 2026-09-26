package com.enterpriseorders.backend.exception;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class PlsqlErrorMapperTest {

    @Test
    void shouldMapOra20001ToTenantAlreadyExists() {
        SQLException ex = new SQLException("ORA-20001: Tenant already exists", "ORA-20001");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(BusinessException.class, result);
        BusinessException be = (BusinessException) result;
        assertEquals("TENANT_ALREADY_EXISTS", be.getCode());
        assertEquals(409, be.getHttpStatus());
    }

    @Test
    void shouldMapOra20011ToInsufficientInventory() {
        SQLException ex = new SQLException("ORA-20011: Insufficient inventory", "ORA-20011");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(BusinessException.class, result);
        BusinessException be = (BusinessException) result;
        assertEquals("INSUFFICIENT_INVENTORY", be.getCode());
        assertEquals(400, be.getHttpStatus());
    }

    @Test
    void shouldMapOra20025ToOrderNotConfirmable() {
        SQLException ex = new SQLException("ORA-20025: Order not confirmable", "ORA-20025");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(BusinessException.class, result);
        BusinessException be = (BusinessException) result;
        assertEquals("ORDER_NOT_CONFIRMABLE", be.getCode());
        assertEquals(400, be.getHttpStatus());
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
        SQLException ex = new SQLException("ORA-20004: Tenant not found", "ORA-20004");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(BusinessException.class, result);
        assertEquals("TENANT_NOT_FOUND", ((BusinessException) result).getCode());
        assertEquals(404, ((BusinessException) result).getHttpStatus());
    }

    @Test
    void shouldMapOra20029ToDuplicatePaymentRef() {
        SQLException ex = new SQLException("ORA-20029: Duplicate payment reference", "ORA-20029");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(BusinessException.class, result);
        assertEquals("DUPLICATE_PAYMENT_REF", ((BusinessException) result).getCode());
        assertEquals(409, ((BusinessException) result).getHttpStatus());
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
        SQLException ex = new SQLException("ORA-20031: Order already paid", "ORA-20031");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(BusinessException.class, result);
        assertEquals("ORDER_ALREADY_PAID", ((BusinessException) result).getCode());
        assertEquals(409, ((BusinessException) result).getHttpStatus());
    }

    @Test
    void shouldMapOra20032ToPaymentAmountInvalid() {
        SQLException ex = new SQLException("ORA-20032: Payment amount must be positive", "ORA-20032");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(BusinessException.class, result);
        assertEquals("PAYMENT_AMOUNT_INVALID", ((BusinessException) result).getCode());
        assertEquals(400, ((BusinessException) result).getHttpStatus());
    }

    @Test
    void shouldMapOra20033ToPaymentCurrencyMismatch() {
        SQLException ex = new SQLException("ORA-20033: Payment currency mismatch", "ORA-20033");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(BusinessException.class, result);
        assertEquals("PAYMENT_CURRENCY_MISMATCH", ((BusinessException) result).getCode());
        assertEquals(400, ((BusinessException) result).getHttpStatus());
    }

    @Test
    void shouldMapOra20034ToShippedNotCancellable() {
        SQLException ex = new SQLException("ORA-20034: Cannot cancel a shipped order", "ORA-20034");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(BusinessException.class, result);
        assertEquals("ORDER_SHIPPED_NOT_CANCELLABLE", ((BusinessException) result).getCode());
        assertEquals(409, ((BusinessException) result).getHttpStatus());
    }

    @Test
    void shouldMapOra20035ToPaymentAmountMismatch() {
        SQLException ex = new SQLException("ORA-20035: Payment amount does not match order total", "ORA-20035");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(BusinessException.class, result);
        assertEquals("PAYMENT_AMOUNT_MISMATCH", ((BusinessException) result).getCode());
        assertEquals(400, ((BusinessException) result).getHttpStatus());
    }

    @Test
    void shouldMapOra20036ToInvalidQuantity() {
        SQLException ex = new SQLException("ORA-20036: Quantity must be greater than zero", "ORA-20036");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(BusinessException.class, result);
        assertEquals("INVALID_QUANTITY", ((BusinessException) result).getCode());
        assertEquals(400, ((BusinessException) result).getHttpStatus());
    }

    @Test
    void shouldMapOra20038ToStatusTransitionConflict() {
        SQLException ex = new SQLException("ORA-20038: Status transition failed: order 1 not found or modified concurrently", "ORA-20038");
        RuntimeException result = PlsqlErrorMapper.map(ex);
        assertInstanceOf(BusinessException.class, result);
        assertEquals("STATUS_TRANSITION_CONFLICT", ((BusinessException) result).getCode());
        assertEquals(409, ((BusinessException) result).getHttpStatus());
    }
}
