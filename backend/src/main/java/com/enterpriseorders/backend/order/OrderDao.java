package com.enterpriseorders.backend.order;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.exception.PlsqlErrorMapper;
import com.enterpriseorders.backend.order.dto.OrderResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
public class OrderDao {

    private final JdbcTemplate jdbcTemplate;

    public OrderDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Reads a single order for a tenant.
     */
    public OrderResponse findOrder(Long tenantId, Long orderId) {
        return jdbcTemplate.query(
                "SELECT order_id, order_number, tenant_id, customer_id, status, " +
                "       currency, total_amount " +
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
                            rs.getBigDecimal("total_amount"));
                },
                orderId, tenantId);
    }

    /**
     * Creates an order via pkg_order_management.create_order (FR-007).
     */
    public Long createOrder(Long tenantId, Long customerId, String currency) {
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

    /**
     * Adds a line to a DRAFT order via
     * pkg_order_management.add_order_line (FR-007).
     */
    public void addOrderLine(Long tenantId, Long orderId, Long productId,
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

    /**
     * Confirms an order atomically (inventory reservation + status +
     * audit + outbox) via pkg_order_management.confirm_order (FR-008,
     * SRS §37, §63).
     */
    public void confirmOrder(Long tenantId, Long orderId, String actor,
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

    /**
     * Cancels an order (releases inventory if confirmed) via
     * pkg_order_management.cancel_order (FR-012, BR-008).
     */
    public void cancelOrder(Long tenantId, Long orderId, String reason,
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

    /**
     * Registers a payment via
     * pkg_order_management.register_payment (FR-011).
     */
    public void registerPayment(Long tenantId, Long orderId, String paymentRef,
                                java.math.BigDecimal amount, String currency,
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

    /**
     * Reads available stock via pkg_inventory.get_available_stock.
     */
    public long getAvailableStock(Long tenantId, Long warehouseId, Long productId) {
        return jdbcTemplate.execute((Connection connection) -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{ ? = call pkg_inventory.get_available_stock(?, ?, ?) }")) {
                cs.registerOutParameter(1, Types.NUMERIC);
                cs.setLong(2, tenantId);
                cs.setLong(3, warehouseId);
                cs.setLong(4, productId);
                cs.execute();
                return cs.getBigDecimal(1).longValue();
            } catch (SQLException ex) {
                throw mapPlsqlError(ex);
            }
        });
    }

    private RuntimeException mapPlsqlError(SQLException ex) {
        return PlsqlErrorMapper.map(ex);
    }
}
</content>