package com.enterpriseorders.backend.order;

import com.enterpriseorders.backend.exception.BusinessException;
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

    /**
     * Maps PL/SQL RAISE_APPLICATION_ERROR codes to functional
     * BusinessExceptions, hiding ORA-* from consumers (SRS §54).
     */
    private BusinessException mapPlsqlError(SQLException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        int http = 400;
        String code;
        String userMessage;

        if (message.contains("ORA-20011")) {
            code = "INSUFFICIENT_INVENTORY";
            userMessage = "Insufficient inventory for product";
        } else if (message.contains("ORA-20020")) {
            code = "TENANT_SUSPENDED";
            userMessage = "Tenant is not ACTIVE";
        } else if (message.contains("ORA-20021")) {
            code = "CUSTOMER_TENANT_MISMATCH";
            userMessage = "Customer does not belong to this tenant";
        } else if (message.contains("ORA-20022")) {
            code = "PRODUCT_TENANT_MISMATCH";
            userMessage = "Product does not belong to this tenant";
        } else if (message.contains("ORA-20023")) {
            code = "ORDER_NOT_DRAFT";
            userMessage = "Cannot add lines to a non-DRAFT order";
        } else if (message.contains("ORA-20024")) {
            code = "ORDER_NOT_FOUND";
            userMessage = "Order or warehouse not found";
            http = 404;
        } else if (message.contains("ORA-20025")) {
            code = "ORDER_NOT_CONFIRMABLE";
            userMessage = "Order cannot be confirmed from current status";
        } else if (message.contains("ORA-20026")) {
            code = "ORDER_NO_LINES";
            userMessage = "Order has no lines - cannot confirm";
        } else if (message.contains("ORA-20027")) {
            code = "ORDER_NOT_CANCELLABLE";
            userMessage = "Order cannot be cancelled from current status";
        } else if (message.contains("ORA-20028")) {
            code = "PAYMENT_STATE_INVALID";
            userMessage = "Payment requires order CONFIRMED or later";
        } else if (message.contains("ORA-20029")) {
            code = "DUPLICATE_PAYMENT_REF";
            userMessage = "Duplicate payment reference";
            http = 409;
        } else if (message.contains("ORA-20010")) {
            code = "INVALID_QUANTITY";
            userMessage = "Reservation quantity must be positive";
        } else if (message.contains("ORA-20014")) {
            code = "INSUFFICIENT_RESERVED";
            userMessage = "Reserved quantity insufficient for release";
        } else {
            code = "DATABASE_ERROR";
            userMessage = "Order operation failed";
            http = 500;
        }
        return new BusinessException(code, userMessage, http);
    }
}
</content>
<task_progress>
- [x] FASE 1-6 completadas (6 commits)
- [x] FASE 7: OrderDao (invoca PL/SQL packages vía JDBC + mapeo errores)
- [ ] FASE 7: Order DTOs (request/response)
- [ ] FASE 7: OrderService (con idempotencia)
- [ ] FASE 7: OrderController (REST endpoints)
- [ ] FASE 7: Commit fase 7
- [ ] FASE 8 a 12 (resto del plan)
</task_progress>