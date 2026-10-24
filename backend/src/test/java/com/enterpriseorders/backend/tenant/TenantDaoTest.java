package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.web.error.GlobalExceptionHandler;
import com.enterpriseorders.backend.tenant.adapter.persistence.TenantDao;
import com.enterpriseorders.backend.tenant.model.NewTenantBO;
import com.enterpriseorders.backend.tenant.model.TenantBO;
import com.enterpriseorders.backend.tenant.port.output.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantDaoTest {
    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private TenantDao tenantDao;

    @Test
    void shouldFindActiveTenantByCodeIncludingTier() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq("INITECH")))
            .thenAnswer(inv -> {
                ResultSetExtractor<Optional<TenantBO>> extractor = inv.getArgument(1);
                var rs = mock(java.sql.ResultSet.class);
                when(rs.next()).thenReturn(true);
                when(rs.getLong("tenant_id")).thenReturn(3L);
                when(rs.getString("tenant_code")).thenReturn("INITECH");
                when(rs.getString("name")).thenReturn("Initech");
                when(rs.getString("tier")).thenReturn("ENTERPRISE");
                when(rs.getString("status")).thenReturn("ACTIVE");
                when(rs.getString("isolation_mode")).thenReturn("SHARED_SCHEMA");
                return extractor.extractData(rs);
            });
        Optional<TenantBO> result = tenantDao.findActiveByCode("INITECH");
        assertTrue(result.isPresent());
        assertEquals(3L, result.get().tenantId());
        assertEquals("ENTERPRISE", result.get().tier());
    }

    @Test
    void shouldReturnEmptyWhenTenantCodeNotFound() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq("UNKNOWN")))
            .thenAnswer(inv -> {
                ResultSetExtractor<Optional<TenantBO>> extractor = inv.getArgument(1);
                var rs = mock(java.sql.ResultSet.class);
                when(rs.next()).thenReturn(false);
                return extractor.extractData(rs);
            });
        Optional<TenantBO> result = tenantDao.findActiveByCode("UNKNOWN");
        assertFalse(result.isPresent());
    }

    @Test
    void shouldFindById() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(1L)))
            .thenAnswer(inv -> {
                ResultSetExtractor<TenantBO> extractor = inv.getArgument(1);
                var rs = mock(java.sql.ResultSet.class);
                when(rs.next()).thenReturn(true);
                when(rs.getLong("tenant_id")).thenReturn(1L);
                when(rs.getString("tenant_code")).thenReturn("ACME");
                when(rs.getString("name")).thenReturn("Acme Corp");
                when(rs.getString("tier")).thenReturn("STANDARD");
                when(rs.getString("status")).thenReturn("ACTIVE");
                when(rs.getString("isolation_mode")).thenReturn("SHARED_SCHEMA");
                return extractor.extractData(rs);
            });
        TenantBO result = tenantDao.findById(1L);
        assertEquals("ACME", result.tenantCode());
    }

    @Test
    void shouldThrowWhenTenantNotFoundById() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(999L)))
            .thenAnswer(inv -> {
                ResultSetExtractor<TenantBO> extractor = inv.getArgument(1);
                var rs = mock(java.sql.ResultSet.class);
                when(rs.next()).thenReturn(false);
                return extractor.extractData(rs);
            });
        assertThrows(BusinessException.class, () -> tenantDao.findById(999L));
    }

    @Test
    void shouldCreateTenantAndReturnId() throws Exception {
        Connection conn = mock(Connection.class);
        CallableStatement cs = mock(CallableStatement.class);
        when(conn.prepareCall(anyString())).thenReturn(cs);
        when(cs.getBigDecimal(1)).thenReturn(BigDecimal.valueOf(7));
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<?> cb = inv.getArgument(0);
            return cb.doInConnection(conn);
        });

        Long id = tenantDao.create(new NewTenantBO(" ACME ", " Acme Corp ", "STANDARD", "SHARED_SCHEMA"));

        assertEquals(7L, id);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareCall(sql.capture());
        assertTrue(sql.getValue().contains("create_tenant"));
        verify(cs).registerOutParameter(1, Types.NUMERIC);
        verify(cs).setString(2, "ACME");
        verify(cs).setString(3, "Acme Corp");
        verify(cs).setString(4, "STANDARD");
        verify(cs).setString(5, "SHARED_SCHEMA");
    }

    @Test
    void shouldMapDuplicateTenantCode() throws Exception {
        Connection conn = mock(Connection.class);
        CallableStatement cs = mock(CallableStatement.class);
        when(conn.prepareCall(anyString())).thenReturn(cs);
        doThrow(new SQLException("ORA-20001: Tenant code already exists")).when(cs).execute();
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<?> cb = inv.getArgument(0);
            return cb.doInConnection(conn);
        });

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tenantDao.create(new NewTenantBO("ACME", "Acme Corp", "STANDARD", "SHARED_SCHEMA")));

        assertEquals("TENANT_ALREADY_EXISTS", ex.getCode());
        assertEquals(HttpStatus.CONFLICT, GlobalExceptionHandler.statusFor(ex.getCode()));
    }
}
