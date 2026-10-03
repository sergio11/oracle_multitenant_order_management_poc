package com.enterpriseorders.backend.order.adapter.persistence;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.exception.DatabaseException;
import com.enterpriseorders.backend.exception.PlsqlErrorMapper;
import com.enterpriseorders.backend.order.model.OrderBO;
import com.enterpriseorders.backend.order.model.OrderBO.OrderLineBO;
import com.enterpriseorders.backend.order.port.output.OrderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC repository for ORDER operations.
 *
 * Reads go through the V_ORDER_DETAIL view (V026): header + lines come
 * back in ONE round trip and are grouped in Java.
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
    public OrderBO findById(Long tenantId, Long orderId) {
        return jdbcTemplate.query(
                "SELECT order_id, order_number, customer_id, status, " +
                "       currency, total_amount, version, " +
                "       order_line_id, product_id, quantity, unit_price, line_total " +
                "FROM v_order_detail " +
                "WHERE order_id = ? AND tenant_id = ? " +
                "ORDER BY order_line_id",
                rs -> {
                    if (!rs.next()) {
                        throw new BusinessException("ORDER_NOT_FOUND",
                                "Order not found: " + orderId);
                    }
                    // First row carries the header (LEFT JOIN keeps lines
                    // nullable so a header-only order still reaches here).
                    Long headerOrderId = rs.getLong("order_id");
                    String orderNumber = rs.getString("order_number");
                    Long customerId = rs.getLong("customer_id");
                    String status = rs.getString("status");
                    String currency = rs.getString("currency");
                    BigDecimal totalAmount = rs.getBigDecimal("total_amount");
                    int version = rs.getInt("version");
                    List<OrderLineBO> lines = new ArrayList<>();
                    do {
                        rs.getLong("order_line_id");
                        if (!rs.wasNull()) {
                            lines.add(new OrderLineBO(
                                    rs.getLong("product_id"),
                                    rs.getLong("quantity"),
                                    rs.getBigDecimal("unit_price"),
                                    rs.getBigDecimal("line_total")));
                        }
                    } while (rs.next());
                    return new OrderBO(headerOrderId, orderNumber, customerId, status,
                            currency, totalAmount, version, lines);
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
