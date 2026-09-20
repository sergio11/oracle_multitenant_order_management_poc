package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.reporting.port.output.dto.OrderDailySummary;
import com.enterpriseorders.backend.reporting.port.output.dto.ProductSalesSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportingDaoTest {
    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private ReportingDao reportingDao;

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetDailyOrders() {
        var summary = new OrderDailySummary(1L, LocalDate.now(), 10L, BigDecimal.valueOf(1000), BigDecimal.valueOf(100));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(inv -> {
            RowMapper<OrderDailySummary> mapper = inv.getArgument(1);
            var rs = mock(java.sql.ResultSet.class);
            when(rs.next()).thenReturn(true, false);
            when(rs.getLong("tenant_id")).thenReturn(1L);
            when(rs.getDate("order_date")).thenReturn(java.sql.Date.valueOf(LocalDate.now()));
            when(rs.getLong("order_count")).thenReturn(10L);
            when(rs.getBigDecimal("total_revenue")).thenReturn(BigDecimal.valueOf(1000));
            when(rs.getBigDecimal("average_order_value")).thenReturn(BigDecimal.valueOf(100));
            return List.of(mapper.mapRow(rs, 0));
        });
        List<OrderDailySummary> result = reportingDao.getDailyOrders();
        assertEquals(1, result.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetTopProducts() {
        var product = new ProductSalesSummary(1L, 1L, LocalDate.now(), 50L, BigDecimal.valueOf(5000), 20L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(10))).thenAnswer(inv -> {
            RowMapper<ProductSalesSummary> mapper = inv.getArgument(1);
            var rs = mock(java.sql.ResultSet.class);
            when(rs.next()).thenReturn(true, false);
            when(rs.getLong("tenant_id")).thenReturn(1L);
            when(rs.getLong("product_id")).thenReturn(1L);
            when(rs.getDate("period")).thenReturn(java.sql.Date.valueOf(LocalDate.now()));
            when(rs.getLong("quantity_sold")).thenReturn(50L);
            when(rs.getBigDecimal("revenue")).thenReturn(BigDecimal.valueOf(5000));
            when(rs.getLong("order_count")).thenReturn(20L);
            return List.of(mapper.mapRow(rs, 0));
        });
        List<ProductSalesSummary> result = reportingDao.getTopProducts(10);
        assertEquals(1, result.size());
        assertEquals(5000, result.get(0).revenue().intValue());
    }

    @Test
    void shouldRefreshMaterializedViews() {
        reportingDao.refreshMaterializedViews();
        verify(jdbcTemplate).execute(contains("DBMS_MVIEW.REFRESH"));
    }
}
