package com.enterpriseorders.backend.order.adapter.persistence;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.exception.DatabaseException;
import com.enterpriseorders.backend.exception.PlsqlErrorMapper;
import com.enterpriseorders.backend.order.model.OrderView;
import com.enterpriseorders.backend.order.model.OrderView.OrderLineView;
import com.enterpriseorders.backend.order.port.output.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * JDBC repository for ORDER operations.
 *
 * All order lifecycle mutations delegate to the PL/SQL packages
 * (SRS §32, §33, §37). This keeps the atomic transaction boundary on
 * the database where it belongs (SRS §61), minimizing round trips.
 */
@Repository
public class OrderDao implements OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OrderView findById(Long tenantId, Long orderId) {
        OrderView order = jdbcTemplate.query(
                "SELECT order_id, order_number, customer_id, status, " +
                "       currency, total_amount, version " +
                "FROM sales_order WHERE order_id = ? AND tenant_id = ?",
                rs -> {
                    if (!rs.next()) {
                        throw new BusinessException("ORDER_NOT_FOUND",
                                "Order not found: " + orderId, HttpStatus.NOT_FOUND);
                    }
                    return new OrderView(
                            rs.getLong("order_id"),
                            rs.getString("order_number"),
                            rs.getLong("customer_id"),
                            rs.getString("status"),
                            rs.getString("currency"),
                            rs.getBigDecimal("total_amount"),
                            rs.getInt("version"),
                            List.of());
                },
                orderId, tenantId);
        List<OrderLineView> lines = jdbcTemplate.query(
                "SELECT product_id, quantity, unit_price, line_total " +
                "FROM sales_order_line WHERE order_id = ? AND tenant_id = ? " +
                "ORDER BY order_line_id",
                (rs, rowNum) -> new OrderLineView(
                        rs.getLong("product_id"),
                        rs.getLong("quantity"),
                        rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("line_total")),
                orderId, tenantId);
        return new OrderView(order.orderId(), order.orderNumber(), order.customerId(),
                order.status(), order.currency(), order.totalAmount(), order.version(), lines);
    }

    @Override
    public Long create(Long tenantId, Long customerId, String currency) {
        return jdbcTemplate.execute((Connection connection) -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{ ? = call pkg_order_management.create_order(?, ?, ?) }")) {
                cs.registerOutParameter(1, Types.NUMERIC);
                cs.setLong(2, tenantId);
                cs.setLong(3, customerId);
                cs.setString(4, currency);
                cs.execute();
                BigDecimal createdId = cs.getBigDecimal(1);
                if (createdId == null) {
                    throw new DatabaseException("DATABASE_ERROR",
                            "create_order returned no order id", null);
                }
                return createdId.longValueExact();
            } catch (SQLException ex) {
                throw mapPlsqlError(ex);
            }
        });
    }

    @Override
    public void addLine(Long tenantId, Long orderId, Long productId, Long quantity) {
        jdbcTemplate.execute((Connection connection) -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{ call pkg_order_management.add_order_line(?, ?, ?, ?, ?) }")) {
                cs.setLong(1, tenantId);
                cs.setLong(2, orderId);
                cs.setLong(3, productId);
                cs.setLong(4, quantity);
                cs.setNull(5, Types.NUMERIC);
                cs.execute();
                return null;
            } catch (SQLException ex) {
                throw mapPlsqlError(ex);
            }
        });
    }

    @Override
    public void confirm(Long tenantId, Long orderId, String actor,
                             String correlationId) {
        jdbcTemplate.execute((Connection connection) -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{ call pkg_order_management.confirm_order(?, ?, ?, ?) }")) {
                cs.setLong(1, tenantId);
                cs.setLong(2, orderId);
                cs.setString(3, actor);
                cs.setString(4, correlationId);
                cs.execute();
                return null;
            } catch (SQLException ex) {
                throw mapPlsqlError(ex);
            }
        });
    }

    @Override
    public void cancel(Long tenantId, Long orderId, String reason,
                            String actor, String correlationId) {
        jdbcTemplate.execute((Connection connection) -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{ call pkg_order_management.cancel_order(?, ?, ?, ?, ?) }")) {
                cs.setLong(1, tenantId);
                cs.setLong(2, orderId);
                cs.setString(3, reason);
                cs.setString(4, actor);
                cs.setString(5, correlationId);
                cs.execute();
                return null;
            } catch (SQLException ex) {
                throw mapPlsqlError(ex);
            }
        });
    }

    @Override
    public void registerPayment(Long tenantId, Long orderId, String paymentRef,
                                BigDecimal amount, String currency,
                                String paymentMethod, String actor,
                                String correlationId) {
        jdbcTemplate.execute((Connection connection) -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{ call pkg_order_management.register_payment(?, ?, ?, ?, ?, ?, ?, ?) }")) {
                cs.setLong(1, tenantId);
                cs.setLong(2, orderId);
                cs.setString(3, paymentRef);
                cs.setBigDecimal(4, amount);
                cs.setString(5, currency);
                cs.setString(6, paymentMethod);
                cs.setString(7, actor);
                cs.setString(8, correlationId);
                cs.execute();
                return null;
            } catch (SQLException ex) {
                throw mapPlsqlError(ex);
            }
        });
    }

    private RuntimeException mapPlsqlError(SQLException ex) {
        return PlsqlErrorMapper.map(ex);
    }
}
