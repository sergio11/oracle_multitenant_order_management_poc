package com.enterpriseorders.backend.order.application;

import com.enterpriseorders.backend.common.domain.OrderStatus;
import com.enterpriseorders.backend.exception.BusinessException;
import com.enterpriseorders.backend.execution.ExecutionContext;
import com.enterpriseorders.backend.order.model.OrderView;
import com.enterpriseorders.backend.order.port.input.AddOrderLineUseCase;
import com.enterpriseorders.backend.order.port.input.CancelOrderUseCase;
import com.enterpriseorders.backend.order.port.input.ConfirmOrderUseCase;
import com.enterpriseorders.backend.order.port.input.CreateOrderUseCase;
import com.enterpriseorders.backend.order.port.input.GetOrderQuery;
import com.enterpriseorders.backend.order.port.input.RegisterPaymentUseCase;
import com.enterpriseorders.backend.order.port.output.OrderRepository;
import com.enterpriseorders.backend.tenant.port.output.TenantContextProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class OrderService implements CreateOrderUseCase, AddOrderLineUseCase,
                                     ConfirmOrderUseCase, CancelOrderUseCase,
                                     RegisterPaymentUseCase, GetOrderQuery {

    /** aggregate_id sentinel for CREATE_ORDER: the order id does not exist yet (V012: NOT NULL). */
    private static final long CREATE_AGGREGATE_ID = 0L;

    private final OrderRepository orderRepository;
    private final IdempotentOperationExecutor idempotentExecutor;
    private final TenantContextProvider tenantContext;
    private final ExecutionContext executionContext;

    public OrderService(OrderRepository orderRepository,
                        IdempotentOperationExecutor idempotentExecutor,
                        TenantContextProvider tenantContext,
                        ExecutionContext executionContext) {
        this.orderRepository = orderRepository;
        this.idempotentExecutor = idempotentExecutor;
        this.tenantContext = tenantContext;
        this.executionContext = executionContext;
    }

    @Transactional
    public OrderView createOrder(Long customerId, String currency, String idempotencyKey) {
        Long tenantId = currentTenantId();
        return idempotentExecutor.execute(tenantId, idempotencyKey, "CREATE_ORDER",
                CREATE_AGGREGATE_ID,
            () -> {
                Long orderId = orderRepository.create(tenantId, customerId, currency);
                return orderRepository.findById(tenantId, orderId);
            });
    }

    @Transactional
    public OrderView addLine(Long orderId, Long productId, Long quantity) {
        Long tenantId = currentTenantId();
        OrderView current = orderRepository.findById(tenantId, orderId);
        OrderStatus status = OrderStatus.fromString(current.status());
        if (status != OrderStatus.DRAFT && status != OrderStatus.PENDING) {
            throw new BusinessException("ORDER_NOT_DRAFT",
                    "Cannot add lines to a non-DRAFT/PENDING order", HttpStatus.BAD_REQUEST);
        }
        orderRepository.addLine(tenantId, orderId, productId, quantity);
        return orderRepository.findById(tenantId, orderId);
    }

    @Transactional
    public OrderView confirmOrder(Long orderId, String idempotencyKey) {
        Long tenantId = currentTenantId();
        return idempotentExecutor.execute(tenantId, idempotencyKey, "CONFIRM_ORDER", orderId,
            () -> {
                OrderView current = orderRepository.findById(tenantId, orderId);
                OrderStatus status = OrderStatus.fromString(current.status());
                if (status != OrderStatus.PENDING) {
                    throw new BusinessException("ORDER_NOT_CONFIRMABLE",
                            "Order cannot be confirmed from current status", HttpStatus.BAD_REQUEST);
                }
                orderRepository.confirm(tenantId, orderId, executionContext.actor(),
                        executionContext.correlationId());
                return orderRepository.findById(tenantId, orderId);
            });
    }

    @Transactional
    public OrderView cancelOrder(Long orderId, String reason, String idempotencyKey) {
        Long tenantId = currentTenantId();
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("CANCEL_REASON_REQUIRED",
                    "Cancellation reason is required", HttpStatus.BAD_REQUEST);
        }
        return idempotentExecutor.execute(tenantId, idempotencyKey, "CANCEL_ORDER", orderId,
            () -> {
                OrderView current = orderRepository.findById(tenantId, orderId);
                OrderStatus status = OrderStatus.fromString(current.status());
                if (status == OrderStatus.SHIPPED) {
                    throw new BusinessException("ORDER_SHIPPED_NOT_CANCELLABLE",
                            "Cannot cancel a shipped order", HttpStatus.CONFLICT);
                }
                if (status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED) {
                    throw new BusinessException("ORDER_NOT_CANCELLABLE",
                            "Order cannot be cancelled from current status", HttpStatus.BAD_REQUEST);
                }
                orderRepository.cancel(tenantId, orderId, reason, executionContext.actor(),
                        executionContext.correlationId());
                return orderRepository.findById(tenantId, orderId);
            });
    }

    @Transactional
    public OrderView registerPayment(Long orderId, String paymentRef,
                                         BigDecimal amount, String currency,
                                         String paymentMethod, String idempotencyKey) {
        Long tenantId = currentTenantId();
        return idempotentExecutor.execute(tenantId, idempotencyKey, "PAYMENT", orderId,
            () -> {
                OrderView current = orderRepository.findById(tenantId, orderId);
                OrderStatus status = OrderStatus.fromString(current.status());
                if (status != OrderStatus.CONFIRMED && status != OrderStatus.ALLOCATED
                        && status != OrderStatus.PAID) {
                    throw new BusinessException("PAYMENT_STATE_INVALID",
                            "Payment requires order CONFIRMED or later", HttpStatus.BAD_REQUEST);
                }
                orderRepository.registerPayment(tenantId, orderId, paymentRef, amount, currency,
                    paymentMethod, executionContext.actor(), executionContext.correlationId());
                return orderRepository.findById(tenantId, orderId);
            });
    }

    @Transactional(readOnly = true)
    public OrderView getOrder(Long orderId) {
        Long tenantId = currentTenantId();
        return orderRepository.findById(tenantId, orderId);
    }

    private Long currentTenantId() {
        Long tenantId = tenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("TENANT_CONTEXT_MISSING",
                    "Tenant context not set", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return tenantId;
    }
}
