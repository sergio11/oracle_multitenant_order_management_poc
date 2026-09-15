package com.enterpriseorders.backend.order;

import com.enterpriseorders.backend.order.dto.CreateOrderRequest;
import com.enterpriseorders.backend.order.dto.OrderLineRequest;
import com.enterpriseorders.backend.order.dto.OrderResponse;
import com.enterpriseorders.backend.order.dto.PaymentRequest;
import com.enterpriseorders.backend.order.port.input.AddOrderLineUseCase;
import com.enterpriseorders.backend.order.port.input.CancelOrderUseCase;
import com.enterpriseorders.backend.order.port.input.ConfirmOrderUseCase;
import com.enterpriseorders.backend.order.port.input.CreateOrderUseCase;
import com.enterpriseorders.backend.order.port.input.GetOrderQuery;
import com.enterpriseorders.backend.order.port.input.RegisterPaymentUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order REST API (SRS §53).
 *
 * POST /api/v1/orders                         -> create (FR-007)
 * GET  /api/v1/orders/{id}                    -> retrieve
 * POST /api/v1/orders/{id}/confirm            -> confirm (FR-008, FR-014)
 * POST /api/v1/orders/{id}/cancel             -> cancel (FR-012)
 * POST /api/v1/orders/{id}/payments           -> payment (FR-011, FR-014)
 *
 * The tenant id is resolved from X-Tenant-ID by TenantContextFilter.
 * Sensitive operations accept an Idempotency-Key header (FR-014).
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final AddOrderLineUseCase addOrderLineUseCase;
    private final ConfirmOrderUseCase confirmOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final RegisterPaymentUseCase registerPaymentUseCase;
    private final GetOrderQuery getOrderQuery;

    public OrderController(CreateOrderUseCase createOrderUseCase,
                           AddOrderLineUseCase addOrderLineUseCase,
                           ConfirmOrderUseCase confirmOrderUseCase,
                           CancelOrderUseCase cancelOrderUseCase,
                           RegisterPaymentUseCase registerPaymentUseCase,
                           GetOrderQuery getOrderQuery) {
        this.createOrderUseCase = createOrderUseCase;
        this.addOrderLineUseCase = addOrderLineUseCase;
        this.confirmOrderUseCase = confirmOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.registerPaymentUseCase = registerPaymentUseCase;
        this.getOrderQuery = getOrderQuery;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createOrderUseCase.createOrder(request.customerId(), request.currency()));
    }

    @PostMapping("/{id}/lines")
    public ResponseEntity<OrderResponse> addLine(
            @PathVariable Long id,
            @Valid @RequestBody OrderLineRequest request) {
        return ResponseEntity.ok(addOrderLineUseCase.addLine(
                id, request.productId(), request.quantity(), request.warehouseId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(getOrderQuery.getOrder(id));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<OrderResponse> confirmOrder(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(confirmOrderUseCase.confirmOrder(id, idempotencyKey));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(cancelOrderUseCase.cancelOrder(id, null));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<OrderResponse> registerPayment(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(registerPaymentUseCase.registerPayment(
                id, request.paymentReference(), request.amount(), request.currency(),
                request.paymentMethod(), idempotencyKey));
    }
}
