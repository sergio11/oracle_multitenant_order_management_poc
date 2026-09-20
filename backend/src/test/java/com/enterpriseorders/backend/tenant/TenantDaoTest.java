package com.enterpriseorders.backend.tenant;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.tenant.dto.TenantResponse;
import com.enterpriseorders.backend.tenant.port.output.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantDaoTest {
    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private TenantDao tenantDao;

    @Test
    void shouldFindActiveTenantIdByCode() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("ACME")))
            .thenAnswer(inv -> {
                RowMapper<Long> mapper = inv.getArgument(1);
                var rs = mock(java.sql.ResultSet.class);
                when(rs.next()).thenReturn(true);
                when(rs.getLong("tenant_id")).thenReturn(1L);
                return Optional.of(mapper.mapRow(rs, 0));
            });
        Optional<Long> result = tenantDao.findActiveTenantIdByCode("ACME");
        assertTrue(result.isPresent());
        assertEquals(1L, result.get());
    }

    @Test
    void shouldReturnEmptyWhenTenantCodeNotFound() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("UNKNOWN")))
            .thenAnswer(inv -> {
                RowMapper<Long> mapper = inv.getArgument(1);
                var rs = mock(java.sql.ResultSet.class);
                when(rs.next()).thenReturn(false);
                return mapper.mapRow(rs, 0);
            });
        Optional<Long> result = tenantDao.findActiveTenantIdByCode("UNKNOWN");
        assertFalse(result.isPresent());
    }

    @Test
    void shouldFindById() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L)))
            .thenAnswer(inv -> {
                RowMapper<TenantResponse> mapper = inv.getArgument(1);
                var rs = mock(java.sql.ResultSet.class);
                when(rs.next()).thenReturn(true);
                when(rs.getLong("tenant_id")).thenReturn(1L);
                when(rs.getString("tenant_code")).thenReturn("ACME");
                when(rs.getString("name")).thenReturn("Acme Corp");
                when(rs.getString("tier")).thenReturn("STANDARD");
                when(rs.getString("status")).thenReturn("ACTIVE");
                when(rs.getString("isolation_mode")).thenReturn("SHARED_SCHEMA");
                return mapper.mapRow(rs, 0);
            });
        TenantResponse result = tenantDao.findById(1L);
        assertEquals("ACME", result.tenantCode());
    }

    @Test
    void shouldThrowWhenTenantNotFoundById() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(999L)))
            .thenAnswer(inv -> {
                RowMapper<TenantResponse> mapper = inv.getArgument(1);
                var rs = mock(java.sql.ResultSet.class);
                when(rs.next()).thenReturn(false);
                return mapper.mapRow(rs, 0);
            });
        assertThrows(BusinessException.class, () -> tenantDao.findById(999L));
    }
}
