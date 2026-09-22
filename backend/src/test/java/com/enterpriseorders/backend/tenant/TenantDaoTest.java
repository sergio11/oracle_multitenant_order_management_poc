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
import org.springframework.jdbc.core.ResultSetExtractor;
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
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq("ACME")))
            .thenAnswer(inv -> {
                ResultSetExtractor<Optional<Long>> extractor = inv.getArgument(1);
                var rs = mock(java.sql.ResultSet.class);
                when(rs.next()).thenReturn(true);
                when(rs.getLong("tenant_id")).thenReturn(1L);
                return extractor.extractData(rs);
            });
        Optional<Long> result = tenantDao.findActiveTenantIdByCode("ACME");
        assertTrue(result.isPresent());
        assertEquals(1L, result.get());
    }

    @Test
    void shouldReturnEmptyWhenTenantCodeNotFound() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq("UNKNOWN")))
            .thenAnswer(inv -> {
                ResultSetExtractor<Optional<Long>> extractor = inv.getArgument(1);
                var rs = mock(java.sql.ResultSet.class);
                when(rs.next()).thenReturn(false);
                return extractor.extractData(rs);
            });
        Optional<Long> result = tenantDao.findActiveTenantIdByCode("UNKNOWN");
        assertFalse(result.isPresent());
    }

    @Test
    void shouldFindById() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(1L)))
            .thenAnswer(inv -> {
                ResultSetExtractor<TenantResponse> extractor = inv.getArgument(1);
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
        TenantResponse result = tenantDao.findById(1L);
        assertEquals("ACME", result.tenantCode());
    }

    @Test
    void shouldThrowWhenTenantNotFoundById() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(999L)))
            .thenAnswer(inv -> {
                ResultSetExtractor<TenantResponse> extractor = inv.getArgument(1);
                var rs = mock(java.sql.ResultSet.class);
                when(rs.next()).thenReturn(false);
                return extractor.extractData(rs);
            });
        assertThrows(BusinessException.class, () -> tenantDao.findById(999L));
    }
}
