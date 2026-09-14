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
}
