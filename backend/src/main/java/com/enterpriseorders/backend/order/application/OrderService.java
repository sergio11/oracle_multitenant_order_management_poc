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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

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
        OrderView result = idempotentExecutor.execute(tenantId, idempotencyKey, "CREATE_ORDER",
                CREATE_AGGREGATE_ID, fingerprint("CREATE_ORDER", customerId, currency),
            () -> {
                Long orderId = orderRepository.create(tenantId, customerId, currency);
                return orderRepository.findById(tenantId, orderId);
            });
        // FR-014 request fingerprint: CREATE_ORDER binds the key to the 0
        // sentinel aggregate (the order does not exist yet), so a reused key
        // with DIFFERENT params would otherwise replay the first order
        // unchanged. Fresh executions and same-param replays always echo the
        // request params; anything else is key misuse -> 409, never a silent
        // wrong-order response.
        if (idempotencyKey != null && !idempotencyKey.isBlank()
                && (!Objects.equals(result.customerId(), customerId)
                        || !Objects.equals(result.currency(), currency))) {
            throw new BusinessException("IDEMPOTENCY_KEY_CONFLICT",
                    "Idempotency key was already used to create a different order; it cannot be reused");
        }
        return result;
    }

    @Transactional
    public OrderView addLine(Long orderId, Long productId, Long quantity) {
        Long tenantId = currentTenantId();
        OrderView current = orderRepository.findById(tenantId, orderId);
        OrderStatus status = OrderStatus.fromString(current.status());
        if (status != OrderStatus.DRAFT && status != OrderStatus.PENDING) {
            throw new BusinessException("ORDER_NOT_DRAFT",
                    "Cannot add lines to a non-DRAFT/PENDING order");
        }
        orderRepository.addLine(tenantId, orderId, productId, quantity);
        return orderRepository.findById(tenantId, orderId);
    }

    @Transactional
    public OrderView confirmOrder(Long orderId, String idempotencyKey) {
        Long tenantId = currentTenantId();
        return idempotentExecutor.execute(tenantId, idempotencyKey, "CONFIRM_ORDER", orderId,
            fingerprint("CONFIRM_ORDER", orderId),
            () -> {
                OrderView current = orderRepository.findById(tenantId, orderId);
                OrderStatus status = OrderStatus.fromString(current.status());
                if (status != OrderStatus.PENDING) {
                    throw new BusinessException("ORDER_NOT_CONFIRMABLE",
                            "Order cannot be confirmed from current status");
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
                    "Cancellation reason is required");
        }
        return idempotentExecutor.execute(tenantId, idempotencyKey, "CANCEL_ORDER", orderId,
            fingerprint("CANCEL_ORDER", orderId, reason),
            () -> {
                OrderView current = orderRepository.findById(tenantId, orderId);
                OrderStatus status = OrderStatus.fromString(current.status());
                if (status == OrderStatus.SHIPPED) {
                    throw new BusinessException("ORDER_SHIPPED_NOT_CANCELLABLE",
                            "Cannot cancel a shipped order");
                }
                if (status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED) {
                    throw new BusinessException("ORDER_NOT_CANCELLABLE",
                            "Order cannot be cancelled from current status");
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
            fingerprint("PAYMENT", orderId, paymentRef, amount, currency, paymentMethod),
            () -> {
                OrderView current = orderRepository.findById(tenantId, orderId);
                OrderStatus status = OrderStatus.fromString(current.status());
                if (status != OrderStatus.CONFIRMED && status != OrderStatus.ALLOCATED
                        && status != OrderStatus.PAID) {
                    throw new BusinessException("PAYMENT_STATE_INVALID",
                            "Payment requires order CONFIRMED or later");
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
                    "Tenant context not set");
        }
        return tenantId;
    }

    /**
     * Request fingerprint for idempotency (V024, closure fix C4). SHA-256
     * hex over the canonical request parameters, unit-separated so
     * ("AB","C") and ("A","BC") can never collide. A retry with the same
     * key but different params gets a different fingerprint and the
     * executor rejects it with IDEMPOTENCY_KEY_CONFLICT instead of
     * replaying the first response.
     */
    private static String fingerprint(Object... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder(64);
            for (Object part : parts) {
                String text = part == null ? "" : part.toString();
                digest.update(text.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1F);
            }
            for (byte b : digest.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable for idempotency fingerprint", e);
        }
    }
}
