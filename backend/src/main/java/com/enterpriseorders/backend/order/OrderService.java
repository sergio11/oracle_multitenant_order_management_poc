package com.enterpriseorders.backend.order;

import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Order lifecycle service (SRS §33, §53, §96).
 *
 * Orchestrates the order workflow (CREATE -> CONFIRM -> PAY -> CANCEL)
 * delegating the atomic DB operations to the PL/SQL packages via
 * OrderDao. Sensitive operations (confirm, pay) implement idempotency
 * via the Idempotency-Key (FR-014, SRS §62, FT-004).
 */
@Service
public class OrderService {

    private final JdbcTemplate jdbcTemplate;
    private final OrderDao orderDao;
    private final IdempotencyDao idempotencyDao;
    private final OutboxDao outboxDao;

    public OrderService(JdbcTemplate jdbcTemplate, OrderDao orderDao,
                        IdempotencyDao idempotencyDao, OutboxDao outboxDao) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderDao = orderDao;
        this.idempotencyDao = idempotencyDao;
        this.outboxDao = outboxDao;
    }

    @Transactional
    public OrderResponse createOrder(Long customerId, String currency) {
        Long tenantId = currentTenantId();
        Long orderId = orderDao.createOrder(tenantId, customerId, currency);
        return orderDao.findOrder(tenantId, orderId);
    }

    @Transactional
    public OrderResponse addLine(Long orderId, Long productId, Long quantity, Long warehouseId) {
        Long tenantId = currentTenantId();
        orderDao.addOrderLine(tenantId, orderId, productId, quantity, warehouseId);
        return orderDao.findOrder(tenantId, orderId);
    }

    /**
     * Confirms an order. Idempotent: repeats with the same Idempotency-Key
     * return the existing result without double-reserving inventory (FR-014).
     * Also publishes an ORDER_CONFIRMED event to the transactional outbox
     * (SRS §63, BR-011).
     */
    @Transactional
    public OrderResponse confirmOrder(Long orderId, String idempotencyKey) {
        Long tenantId = currentTenantId();

        // Idempotency: return existing result on retry (FR-014, FT-004)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyDao.findByKey(tenantId, idempotencyKey);
            if (existing.isPresent()) {
                return orderDao.findOrder(tenantId, existing.get().aggregateId());
            }
            // Insert PENDING record to acquire lock and prevent concurrent execution
            if (!idempotencyDao.insertPending(tenantId, idempotencyKey, "CONFIRM_ORDER", orderId)) {
                // Another request inserted first - return existing result
                var raceResult = idempotencyDao.findByKey(tenantId, idempotencyKey);
                if (raceResult.isPresent()) {
                    return orderDao.findOrder(tenantId, raceResult.get().aggregateId());
                }
            }
        }

        String actor = "APP_JAVA";
        orderDao.confirmOrder(tenantId, orderId, actor, MDC.get("correlationId"));

        // Mark idempotency as completed
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyDao.markCompleted(tenantId, idempotencyKey, "{\"orderId\":" + orderId + "}");
        }

        // Publish ORDER_CONFIRMED event to transactional outbox
        outboxDao.writeEvent(tenantId, "ORDER", String.valueOf(orderId),
                "ORDER_CONFIRMED", "{\"orderId\":" + orderId + ",\"tenantId\":" + tenantId + "}");

        return orderDao.findOrder(tenantId, orderId);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, String reason) {
        Long tenantId = currentTenantId();
        orderDao.cancelOrder(tenantId, orderId, reason, "APP_JAVA", MDC.get("correlationId"));

        // Publish ORDER_CANCELLED event to transactional outbox
        outboxDao.writeEvent(tenantId, "ORDER", String.valueOf(orderId),
                "ORDER_CANCELLED", "{\"orderId\":" + orderId + ",\"tenantId\":" + tenantId + "}");

        return orderDao.findOrder(tenantId, orderId);
    }

    /**
     * Registers a payment. Idempotent by payment reference (FR-011,
     * FR-014). The unique payment_reference constraint prevents double
     * payment under retries.
     */
    @Transactional
    public OrderResponse registerPayment(Long orderId, String paymentRef,
                                         BigDecimal amount, String currency,
                                         String paymentMethod, String idempotencyKey) {
        Long tenantId = currentTenantId();

        // Idempotency by payment reference
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyDao.findByKey(tenantId, idempotencyKey);
            if (existing.isPresent()) {
                return orderDao.findOrder(tenantId, existing.get().aggregateId());
            }
            if (!idempotencyDao.insertPending(tenantId, idempotencyKey, "PAYMENT", orderId)) {
                var raceResult = idempotencyDao.findByKey(tenantId, idempotencyKey);
                if (raceResult.isPresent()) {
                    return orderDao.findOrder(tenantId, raceResult.get().aggregateId());
                }
            }
        }

        orderDao.registerPayment(tenantId, orderId, paymentRef, amount, currency,
                paymentMethod, "APP_JAVA", MDC.get("correlationId"));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyDao.markCompleted(tenantId, idempotencyKey, "{\"orderId\":" + orderId + "}");
        }

        // Publish PAYMENT_REGISTERED event to transactional outbox
        outboxDao.writeEvent(tenantId, "ORDER", String.valueOf(orderId),
                "PAYMENT_REGISTERED", "{\"orderId\":" + orderId + ",\"paymentRef\":\"" + paymentRef + "\"}");

        return orderDao.findOrder(tenantId, orderId);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Long tenantId = currentTenantId();
        return orderDao.findOrder(tenantId, orderId);
    }

    private Long currentTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        return tenantId;
    }
}
