package com.enterpriseorders.backend.order.adapter.persistence;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.exception.DatabaseException;
import com.enterpriseorders.backend.web.error.GlobalExceptionHandler;
import com.enterpriseorders.backend.order.model.OrderBO;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderDaoTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @InjectMocks
    private OrderDao orderDao;

    /**
     * Feeds the single V_ORDER_DETAIL result set consumed by findById:
     * row 0 always carries the header; row r additionally carries line r
     * (LEFT JOIN: the header row has null line columns when the order has
     * no lines).
     */
    @SuppressWarnings("unchecked")
    private void stubFindById(boolean found, OrderBO.OrderLineBO... lines) {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq(1L), eq(9L)))
                .thenAnswer(inv -> {
                    ResultSetExtractor<OrderBO> extractor = inv.getArgument(1);
                    var rs = mock(java.sql.ResultSet.class);
                    int rowCount = found ? Math.max(1, lines.length) : 0;
                    var idx = new java.util.concurrent.atomic.AtomicInteger(-1);
                    boolean[] wasNull = {false};
                    when(rs.next()).thenAnswer(i -> idx.incrementAndGet() < rowCount);
                    if (found) {
                        lenient().when(rs.getLong("order_id"))
                                .thenAnswer(i -> { wasNull[0] = false; return 1L; });
                        lenient().when(rs.getString("order_number"))
                                .thenAnswer(i -> { wasNull[0] = false; return "ORD-001"; });
                        lenient().when(rs.getLong("customer_id"))
                                .thenAnswer(i -> { wasNull[0] = false; return 100L; });
                        lenient().when(rs.getString("status"))
                                .thenAnswer(i -> { wasNull[0] = false; return "DRAFT"; });
                        lenient().when(rs.getString("currency"))
                                .thenAnswer(i -> { wasNull[0] = false; return "USD"; });
                        lenient().when(rs.getBigDecimal("total_amount"))
                                .thenAnswer(i -> { wasNull[0] = false; return new BigDecimal("250.00"); });
                        lenient().when(rs.getInt("version"))
                                .thenAnswer(i -> { wasNull[0] = false; return 0; });
                        lenient().when(rs.getLong("order_line_id")).thenAnswer(i -> {
                            if (idx.get() < 0 || idx.get() >= lines.length) {
                                wasNull[0] = true;
                                return 0L;
                            }
                            wasNull[0] = false;
                            return (long) (idx.get() + 1);
                        });
                        lenient().when(rs.getLong("product_id")).thenAnswer(i -> {
                            if (idx.get() < 0 || idx.get() >= lines.length) {
                                wasNull[0] = true;
                                return 0L;
                            }
                            wasNull[0] = false;
                            return lines[idx.get()].productId();
                        });
                        lenient().when(rs.getLong("quantity")).thenAnswer(i -> {
                            if (idx.get() < 0 || idx.get() >= lines.length) {
                                wasNull[0] = true;
                                return 0L;
                            }
                            wasNull[0] = false;
                            return lines[idx.get()].quantity();
                        });
                        lenient().when(rs.getBigDecimal("unit_price")).thenAnswer(i -> {
                            if (idx.get() < 0 || idx.get() >= lines.length) {
                                wasNull[0] = true;
                                return null;
                            }
                            wasNull[0] = false;
                            return lines[idx.get()].unitPrice();
                        });
                        lenient().when(rs.getBigDecimal("line_total")).thenAnswer(i -> {
                            if (idx.get() < 0 || idx.get() >= lines.length) {
                                wasNull[0] = true;
                                return null;
                            }
                            wasNull[0] = false;
                            return lines[idx.get()].lineTotal();
                        });
                        lenient().when(rs.wasNull()).thenAnswer(i -> wasNull[0]);
                    }
                    return extractor.extractData(rs);
                });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CallableStatement stubConnectionCallback(BigDecimal outId) throws Exception {
        Connection conn = mock(Connection.class);
        CallableStatement cs = mock(CallableStatement.class);
        when(conn.prepareCall(anyString())).thenReturn(cs);
        if (outId != null) {
            when(cs.getBigDecimal(1)).thenReturn(outId);
        }
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback cb = inv.getArgument(0);
            return cb.doInConnection(conn);
        });
        return cs;
    }

    @Test
    void shouldFindByIdWithLinesInSingleQuery() {
        stubFindById(true,
                new OrderBO.OrderLineBO(5L, 2L, new BigDecimal("100.00"), new BigDecimal("200.00")));

        OrderBO result = orderDao.findById(9L, 1L);

        assertEquals(1L, result.orderId());
        assertEquals("ORD-001", result.orderNumber());
        assertEquals(100L, result.customerId());
        assertEquals("DRAFT", result.status());
        assertEquals("USD", result.currency());
        assertEquals(0, result.version());
        assertEquals(1, result.lines().size());
        assertEquals(5L, result.lines().get(0).productId());
        assertEquals(2L, result.lines().get(0).quantity());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(1))
                .query(sql.capture(), any(ResultSetExtractor.class), eq(1L), eq(9L));
        assertTrue(sql.getValue().contains("v_order_detail"));
    }

    @Test
    void shouldFindByIdWithEmptyLines() {
        stubFindById(true);

        OrderBO result = orderDao.findById(9L, 1L);

        assertEquals(1L, result.orderId());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void shouldThrowOrderNotFound() {
        stubFindById(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> orderDao.findById(9L, 1L));

        assertEquals("ORDER_NOT_FOUND", ex.getCode());
        assertEquals(HttpStatus.NOT_FOUND, GlobalExceptionHandler.statusFor(ex.getCode()));
    }

    @Test
    void shouldCreateAndReturnId() throws Exception {
        Connection conn = mock(Connection.class);
        CallableStatement cs = mock(CallableStatement.class);
        when(conn.prepareCall(anyString())).thenReturn(cs);
        when(cs.getBigDecimal(1)).thenReturn(BigDecimal.valueOf(42));
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<?> cb = inv.getArgument(0);
            return cb.doInConnection(conn);
        });

        Long id = orderDao.create(9L, 100L, "USD");

        assertEquals(42L, id);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(conn, times(1)).prepareCall(sqlCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("create_order"));
        verify(cs).registerOutParameter(1, Types.NUMERIC);
        verify(cs).setLong(2, 9L);
        verify(cs).setLong(3, 100L);
        verify(cs).setString(4, "USD");
    }

    @Test
    void shouldThrowDatabaseErrorWhenCreateReturnsNull() throws Exception {
        stubConnectionCallback(null);

        assertThrows(DatabaseException.class, () -> orderDao.create(9L, 100L, "USD"));
    }

    @Test
    void shouldMapCreatePlsqlError() throws Exception {
        Connection conn = mock(Connection.class);
        CallableStatement cs = mock(CallableStatement.class);
        when(conn.prepareCall(anyString())).thenReturn(cs);
        doThrow(new SQLException("ORA-20024: Order or warehouse not found")).when(cs).execute();
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<?> cb = inv.getArgument(0);
            return cb.doInConnection(conn);
        });

        BusinessException ex = assertThrows(BusinessException.class, () -> orderDao.create(9L, 100L, "USD"));

        assertEquals("ORDER_NOT_FOUND", ex.getCode());
        assertEquals(HttpStatus.NOT_FOUND, GlobalExceptionHandler.statusFor(ex.getCode()));
    }

    @Test
    void shouldAddLineWithBoundParams() throws Exception {
        Connection conn = mock(Connection.class);
        CallableStatement cs = mock(CallableStatement.class);
        when(conn.prepareCall(anyString())).thenReturn(cs);
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<?> cb = inv.getArgument(0);
            return cb.doInConnection(conn);
        });

        orderDao.addLine(9L, 1L, 5L, 2L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareCall(sql.capture());
        assertTrue(sql.getValue().contains("add_order_line"));
        verify(cs).setLong(1, 9L);
        verify(cs).setLong(2, 1L);
        verify(cs).setLong(3, 5L);
        verify(cs).setLong(4, 2L);
        verify(cs).setNull(5, Types.NUMERIC);
        verify(cs).execute();
    }

    @Test
    void shouldMapAddLinePlsqlError() throws Exception {
        Connection conn = mock(Connection.class);
        CallableStatement cs = mock(CallableStatement.class);
        when(conn.prepareCall(anyString())).thenReturn(cs);
        doThrow(new SQLException("ORA-20023: Cannot add lines")).when(cs).execute();
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<?> cb = inv.getArgument(0);
            return cb.doInConnection(conn);
        });

        BusinessException ex = assertThrows(BusinessException.class, () -> orderDao.addLine(9L, 1L, 5L, 2L));

        assertEquals("ORDER_NOT_DRAFT", ex.getCode());
    }

    @Test
    void shouldConfirmWithBoundParams() throws Exception {
        Connection conn = mock(Connection.class);
        CallableStatement cs = mock(CallableStatement.class);
        when(conn.prepareCall(anyString())).thenReturn(cs);
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<?> cb = inv.getArgument(0);
            return cb.doInConnection(conn);
        });

        orderDao.confirm(9L, 1L, "APP_JAVA", "corr-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareCall(sql.capture());
        assertTrue(sql.getValue().contains("confirm_order"));
        verify(cs).setLong(1, 9L);
        verify(cs).setLong(2, 1L);
        verify(cs).setString(3, "APP_JAVA");
        verify(cs).setString(4, "corr-1");
    }

    @Test
    void shouldCancelWithBoundParams() throws Exception {
        Connection conn = mock(Connection.class);
        CallableStatement cs = mock(CallableStatement.class);
        when(conn.prepareCall(anyString())).thenReturn(cs);
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<?> cb = inv.getArgument(0);
            return cb.doInConnection(conn);
        });

        orderDao.cancel(9L, 1L, "changed mind", "APP_JAVA", "corr-2");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareCall(sql.capture());
        assertTrue(sql.getValue().contains("cancel_order"));
        verify(cs).setLong(1, 9L);
        verify(cs).setLong(2, 1L);
        verify(cs).setString(3, "changed mind");
        verify(cs).setString(4, "APP_JAVA");
        verify(cs).setString(5, "corr-2");
    }

    @Test
    void shouldRegisterPaymentWithBoundParams() throws Exception {
        Connection conn = mock(Connection.class);
        CallableStatement cs = mock(CallableStatement.class);
        when(conn.prepareCall(anyString())).thenReturn(cs);
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<?> cb = inv.getArgument(0);
            return cb.doInConnection(conn);
        });

        orderDao.registerPayment(9L, 1L, "PAY-1", new BigDecimal("250.00"), "USD", "CARD", "APP_JAVA", "corr-3");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(conn).prepareCall(sql.capture());
        assertTrue(sql.getValue().contains("register_payment"));
        verify(cs).setLong(1, 9L);
        verify(cs).setLong(2, 1L);
        verify(cs).setString(3, "PAY-1");
        verify(cs).setBigDecimal(4, new BigDecimal("250.00"));
        verify(cs).setString(5, "USD");
        verify(cs).setString(6, "CARD");
        verify(cs).setString(7, "APP_JAVA");
        verify(cs).setString(8, "corr-3");
    }

    @Test
    void shouldMapConfirmPlsqlError() throws Exception {
        Connection conn = mock(Connection.class);
        CallableStatement cs = mock(CallableStatement.class);
        when(conn.prepareCall(anyString())).thenReturn(cs);
        doThrow(new SQLException("ORA-20025: cannot confirm")).when(cs).execute();
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(inv -> {
            ConnectionCallback<?> cb = inv.getArgument(0);
            return cb.doInConnection(conn);
        });

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderDao.confirm(9L, 1L, "APP_JAVA", "corr-1"));

        assertEquals("ORDER_NOT_CONFIRMABLE", ex.getCode());
    }
}
