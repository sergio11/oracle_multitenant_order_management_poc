package com.enterpriseorders.backend.order.dto;

import com.enterpriseorders.backend.order.model.OrderView;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response payload for order operations (SRS ��53).
 */
public record OrderResponse(
        Long orderId,
        String orderNumber,
        Long customerId,
        String status,
        String currency,
        BigDecimal totalAmount,
        int version,
        List<OrderLineView> lines
) {
    public OrderResponse {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public record OrderLineView(
            Long productId,
            Long quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
    }

    /** Maps the neutral port model to the web-facing DTO at the edge. */
    public static OrderResponse from(OrderView view) {
        return new OrderResponse(
                view.orderId(),
                view.orderNumber(),
                view.customerId(),
                view.status(),
                view.currency(),
                view.totalAmount(),
                view.version(),
                view.lines().stream()
                        .map(l -> new OrderLineView(
                                l.productId(), l.quantity(), l.unitPrice(), l.lineTotal()))
                        .toList());
    }
}
