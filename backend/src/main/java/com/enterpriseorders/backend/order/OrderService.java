package com.enterpriseorders.backend.order;

import com.enterpriseorders.backend.order.dto.OrderResponse;
import com.enterpriseorders.backend.order.infrastructure.IdempotentOperationExecutor;
import com.enterpriseorders.backend.order.port.input.AddOrderLineUseCase;
import com.enterpriseorders.backend.order.port.input.CancelOrderUseCase;
import com.enterpriseorders.backend.order.port.input.ConfirmOrderUseCase;
import com.enterpriseorders.backend.order.port.input.CreateOrderUseCase;
import com.enterpriseorders.backend.order.port.input.GetOrderQuery;
import com.enterpriseorders.backend.order.port.input.RegisterPaymentUseCase;
import com.enterpriseorders.backend.order.port.output.IdempotencyStore;
import com.enterpriseorders.backend.order.port.output.OrderRepository;
import com.enterpriseorders.backend.tenant.port.output.TenantContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Order lifecycle service (SRS §33, §53, §96).
 *
 * Orchestrates the order workflow (CREATE -> CONFIRM -> PAY -> CANCEL)
 * delegating the atomic DB operations to the PL/SQL packages via
 * OrderRepository. Sensitive operations (confirm, pay) implement idempotency
 * via the IdempotentOperationExecutor (FR-014, SRS §62, FT-004).
 *
 * Outbox events are written ATOMICALLY by PL/SQL within the same
 * transaction as the business mutation (SRS §63, BR-011). Java does
 * NOT write outbox events to avoid duplication.
 */
@Service
public class OrderService implements CreateOrderUseCase, AddOrderLineUseCase,
                                     ConfirmOrderUseCase, CancelOrderUseCase,
                                     RegisterPaymentUseCase, GetOrderQuery {

    private static final String ACTOR = "APP_JAVA";

    private final OrderRepository orderRepository;
    private final IdempotentOperationExecutor idempotentExecutor;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final TenantContextProvider tenantContext;

    public OrderService(OrderRepository orderRepository,
                        IdempotentOperationExecutor idempotentExecutor,
                        IdempotencyStore idempotencyStore,
                        ObjectMapper objectMapper, TenantContextProvider tenantContext) {
        this.orderRepository = orderRepository;
        this.idempotentExecutor = idempotentExecutor;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public OrderResponse createOrder(Long customerId, String currency) {
        Long tenantId = currentTenantId();
        Long orderId = orderRepository.create(tenantId, customerId, currency);
        return orderRepository.findById(tenantId, orderId);
    }

    @Transactional
    public OrderResponse addLine(Long orderId, Long productId, Long quantity, Long warehouseId) {
        Long tenantId = currentTenantId();
        orderRepository.addLine(tenantId, orderId, productId, quantity, warehouseId);
        return orderRepository.findById(tenantId, orderId);
    }

    /**
     * Confirms an order. Idempotent: repeats with the same Idempotency-Key
     * return the existing result without double-reserving inventory (FR-014).
     * Outbox event is written atomically by PL/SQL within confirm_order (SRS §63).
     */
    @Transactional
    public OrderResponse confirmOrder(Long orderId, String idempotencyKey) {
        Long tenantId = currentTenantId();

        OrderResponse result = idempotentExecutor.execute(tenantId, idempotencyKey, "CONFIRM_ORDER", orderId,
            () -> {
                orderRepository.confirm(tenantId, orderId, ACTOR, MDC.get("correlationId"));
                return orderRepository.findById(tenantId, orderId);
            });

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                idempotencyStore.markCompleted(tenantId, idempotencyKey,
                    objectMapper.writeValueAsString(java.util.Map.of("orderId", orderId)));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize idempotency result", e);
            }
        }

        return result;
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId, String reason) {
        Long tenantId = currentTenantId();
        orderRepository.cancel(tenantId, orderId, reason, ACTOR, MDC.get("correlationId"));
        return orderRepository.findById(tenantId, orderId);
    }

    /**
     * Registers a payment. Idempotent by payment reference (FR-011,
     * FR-014). The unique payment_reference constraint prevents double
     * payment under retries. Outbox event is written atomically by PL/SQL.
     */
    @Transactional
    public OrderResponse registerPayment(Long orderId, String paymentRef,
                                         BigDecimal amount, String currency,
                                         String paymentMethod, String idempotencyKey) {
        Long tenantId = currentTenantId();

        OrderResponse result = idempotentExecutor.execute(tenantId, idempotencyKey, "PAYMENT", orderId,
            () -> {
                orderRepository.registerPayment(tenantId, orderId, paymentRef, amount, currency,
                    paymentMethod, ACTOR, MDC.get("correlationId"));
                return orderRepository.findById(tenantId, orderId);
            });

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                idempotencyStore.markCompleted(tenantId, idempotencyKey,
                    objectMapper.writeValueAsString(java.util.Map.of("orderId", orderId, "paymentRef", paymentRef)));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize idempotency result", e);
            }
        }

        return result;
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Long tenantId = currentTenantId();
        return orderRepository.findById(tenantId, orderId);
    }

    private Long currentTenantId() {
        Long tenantId = tenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context not set");
        }
        return tenantId;
    }
}
