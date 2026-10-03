package com.enterpriseorders.backend.order.adapter.web;

import com.enterpriseorders.backend.order.dto.CancelOrderRequestDTO;
import com.enterpriseorders.backend.order.dto.CreateOrderRequestDTO;
import com.enterpriseorders.backend.order.dto.OrderLineRequestDTO;
import com.enterpriseorders.backend.order.dto.OrderResponseDTO;
import com.enterpriseorders.backend.order.dto.PaymentRequestDTO;
import com.enterpriseorders.backend.order.adapter.web.mapper.OrderMapper;
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

import java.net.URI;

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
 * Sensitive operations accept an optional Idempotency-Key header (FR-014);
 * without it the operation is not idempotent.
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
    private final OrderMapper orderMapper;

    public OrderController(CreateOrderUseCase createOrderUseCase,
                           AddOrderLineUseCase addOrderLineUseCase,
                           ConfirmOrderUseCase confirmOrderUseCase,
                           CancelOrderUseCase cancelOrderUseCase,
                           RegisterPaymentUseCase registerPaymentUseCase,
                           GetOrderQuery getOrderQuery,
                           OrderMapper orderMapper) {
        this.createOrderUseCase = createOrderUseCase;
        this.addOrderLineUseCase = addOrderLineUseCase;
        this.confirmOrderUseCase = confirmOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.registerPaymentUseCase = registerPaymentUseCase;
        this.getOrderQuery = getOrderQuery;
        this.orderMapper = orderMapper;
    }

    @PostMapping
    public ResponseEntity<OrderResponseDTO> createOrder(
            @Valid @RequestBody CreateOrderRequestDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        OrderResponseDTO created = orderMapper.toResponse(createOrderUseCase.createOrder(
                request.customerId(), request.currency(), idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/orders/" + created.orderId()))
                .body(created);
    }

    @PostMapping("/{id}/lines")
    public ResponseEntity<OrderResponseDTO> addLine(
            @PathVariable Long id,
            @Valid @RequestBody OrderLineRequestDTO request) {
        return ResponseEntity.ok(orderMapper.toResponse(addOrderLineUseCase.addLine(
                id, request.productId(), request.quantity())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderMapper.toResponse(getOrderQuery.getOrder(id)));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<OrderResponseDTO> confirmOrder(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(orderMapper.toResponse(confirmOrderUseCase.confirmOrder(id, idempotencyKey)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponseDTO> cancelOrder(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CancelOrderRequestDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String reason = request != null ? request.reason() : null;
        return ResponseEntity.ok(orderMapper.toResponse(cancelOrderUseCase.cancelOrder(id, reason, idempotencyKey)));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<OrderResponseDTO> registerPayment(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequestDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(orderMapper.toResponse(registerPaymentUseCase.registerPayment(
                id, request.paymentReference(), request.amount(), request.currency(),
                request.paymentMethod(), idempotencyKey)));
    }
}
