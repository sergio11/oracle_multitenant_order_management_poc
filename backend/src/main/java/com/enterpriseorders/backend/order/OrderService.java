package com.enterpriseorders.backend.order;

import com.enterpriseorders.backend.order.dto.OrderResponse;
import com.enterpriseorders.backend.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Supplier;

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

    private static final String ACTOR = "APP_JAVA";

    private final OrderDao orderDao;
    private final IdempotencyDao idempotencyDao;
    private final OutboxDao outboxDao;
    private final ObjectMapper objectMapper;

    public OrderService(OrderDao orderDao, IdempotencyDao idempotencyDao,
                        OutboxDao outboxDao, ObjectMapper objectMapper) {
        this.orderDao = orderDao;
        this.idempotencyDao = idempotencyDao;
        this.outboxDao = outboxDao;
        this.objectMapper = objectMapper;
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

        OrderResponse result = executeIdempotent(tenantId, idempotencyKey, "CONFIRM_ORDER", orderId,
            () -> {
                orderDao.confirmOrder(tenantId, orderId, ACTOR, MDC.get("correlationId"));
                return orderDao.findOrder(tenantId, orderId);
            });

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                idempotencyDao.markCompleted(tenantId, idempotencyKey,
                    objectMapper.writeValueAsString(Map.of("orderId", orderId)));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize idempotency result", e);
            }
        }

        try {
            outboxDao.writeEvent(tenantId, "ORDER", String.valueOf(orderId),
                "ORDER_CONFIRMED", objectMapper.writeValueAsString(Map.of("orderId", orderId, "tenantId", tenantId)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }

        return result;
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, String reason) {
        Long tenantId = currentTenantId();
        orderDao.cancelOrder(tenantId, orderId, reason, ACTOR, MDC.get("correlationId"));

        try {
            outboxDao.writeEvent(tenantId, "ORDER", String.valueOf(orderId),
                "ORDER_CANCELLED", objectMapper.writeValueAsString(Map.of("orderId", orderId, "tenantId", tenantId)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }

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

        OrderResponse result = executeIdempotent(tenantId, idempotencyKey, "PAYMENT", orderId,
            () -> {
                orderDao.registerPayment(tenantId, orderId, paymentRef, amount, currency,
                    paymentMethod, ACTOR, MDC.get("correlationId"));
                return orderDao.findOrder(tenantId, orderId);
            });

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                idempotencyDao.markCompleted(tenantId, idempotencyKey,
                    objectMapper.writeValueAsString(Map.of("orderId", orderId)));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize idempotency result", e);
            }
        }

        try {
            outboxDao.writeEvent(tenantId, "ORDER", String.valueOf(orderId),
                "PAYMENT_REGISTERED", objectMapper.writeValueAsString(Map.of("orderId", orderId, "paymentRef", paymentRef)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Long tenantId = currentTenantId();
        return orderDao.findOrder(tenantId, orderId);
    }

    private OrderResponse executeIdempotent(Long tenantId, String idempotencyKey,
                                             String operation, Long orderId,
                                             Supplier<OrderResponse> action) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyDao.findByKey(tenantId, idempotencyKey);
            if (existing.isPresent()) {
                return orderDao.findOrder(tenantId, existing.get().aggregateId());
            }
            if (!idempotencyDao.insertPending(tenantId, idempotencyKey, operation, orderId)) {
                var raceResult = idempotencyDao.findByKey(tenantId, idempotencyKey);
                if (raceResult.isPresent()) {
                    return orderDao.findOrder(tenantId, raceResult.get().aggregateId());
                }
            }
        }
        return action.get();
    }

    private Long currentTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        return tenantId;
    }
}
