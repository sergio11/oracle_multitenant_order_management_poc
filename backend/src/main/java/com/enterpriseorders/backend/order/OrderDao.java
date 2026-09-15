package com.enterpriseorders.backend.order;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.exception.PlsqlErrorMapper;
import com.enterpriseorders.backend.order.dto.OrderResponse;
import com.enterpriseorders.backend.order.port.output.OrderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

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
    public OrderResponse findById(Long tenantId, Long orderId) {
        return jdbcTemplate.query(
                "SELECT order_id, order_number, tenant_id, customer_id, status, " +
                "       currency, total_amount, version " +
                "FROM sales_order WHERE order_id = ? AND tenant_id = ?",
                rs -> {
                    if (!rs.next()) {
                        throw new BusinessException("ORDER_NOT_FOUND",
                                "Order not found: " + orderId, 404);
                    }
                    return new OrderResponse(
                            rs.getLong("order_id"),
                            rs.getString("order_number"),
                            rs.getLong("tenant_id"),
                            rs.getLong("customer_id"),
                            rs.getString("status"),
                            rs.getString("currency"),
                            rs.getBigDecimal("total_amount"),
                            rs.getInt("version"));
                },
                orderId, tenantId);
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
                return cs.getBigDecimal(1).longValue();
            } catch (SQLException ex) {
                throw mapPlsqlError(ex);
            }
        });
    }

    @Override
    public void addLine(Long tenantId, Long orderId, Long productId,
                             Long quantity, Long warehouseId) {
        jdbcTemplate.execute((Connection connection) -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{ call pkg_order_management.add_order_line(?, ?, ?, ?, ?) }")) {
                cs.setLong(1, tenantId);
                cs.setLong(2, orderId);
                cs.setLong(3, productId);
                cs.setLong(4, quantity);
                cs.setLong(5, warehouseId);
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
