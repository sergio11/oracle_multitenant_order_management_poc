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

    public OrderService(JdbcTemplate jdbcTemplate, OrderDao orderDao,
                        IdempotencyDao idempotencyDao) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderDao = orderDao;
        this.idempotencyDao = idempotencyDao;
    }

    @Transactional
    public OrderResponse createOrder(Long customerId, String currency) {
        Long tenantId = TenantContext.getTenantId();
        Long orderId = orderDao.createOrder(tenantId, customerId, currency);
        return orderDao.findOrder(tenantId, orderId);
    }

    @Transactional
    public OrderResponse addLine(Long orderId, Long productId, Long quantity, Long warehouseId) {
        Long tenantId = TenantContext.getTenantId();
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
        Long tenantId = TenantContext.getTenantId();

        // Idempotency: return existing result on retry (FR-014, FT-004)
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyDao.findByKey(tenantId, idempotencyKey);
            if (existing.isPresent()) {
                return orderDao.findOrder(tenantId, existing.get().aggregateId());
            }
        }

        String actor = "APP_JAVA";
        orderDao.confirmOrder(tenantId, orderId, actor, MDC.get("correlationId"));

        // Record idempotency key AFTER successful confirm
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyDao.insertIfAbsent(tenantId, idempotencyKey, "CONFIRM_ORDER", orderId);
        }

        return orderDao.findOrder(tenantId, orderId);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, String reason) {
        Long tenantId = TenantContext.getTenantId();
        orderDao.cancelOrder(tenantId, orderId, reason, "APP_JAVA", MDC.get("correlationId"));
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
                                         String paymentMethod) {
        Long tenantId = TenantContext.getTenantId();

        // Idempotency by payment reference
        if (paymentRef != null && !paymentRef.isBlank()) {
            var existing = idempotencyDao.findByKey(tenantId, "PAY:" + paymentRef);
            if (existing.isPresent()) {
                return orderDao.findOrder(tenantId, existing.get().aggregateId());
            }
        }

        orderDao.registerPayment(tenantId, orderId, paymentRef, amount, currency,
                paymentMethod, "APP_JAVA", MDC.get("correlationId"));

        if (paymentRef != null && !paymentRef.isBlank()) {
            idempotencyDao.insertIfAbsent(tenantId, "PAY:" + paymentRef, "PAYMENT", orderId);
        }

        return orderDao.findOrder(tenantId, orderId);
    }

    public OrderResponse getOrder(Long orderId) {
        return orderDao.findOrder(TenantContext.getTenantId(), orderId);
    }
}
