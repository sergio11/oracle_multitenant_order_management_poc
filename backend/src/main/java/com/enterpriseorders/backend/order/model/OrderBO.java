package com.enterpriseorders.backend.order.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Neutral read model for orders (A3).
 *
 * <p>Same shape as the web-facing {@code dto.OrderResponseDTO} (field names and
 * order identical, so idempotency JSON payloads stay interchangeable), but
 * free of web concerns: input/output ports, the application service and the
 * persistence adapters depend on this type, never on web DTOs. The web edge
 * maps {@code OrderBO} to {@code OrderResponseDTO} with MapStruct
 * ({@code adapter.web.mapper.OrderMapper}).
 */
public record OrderBO(
        Long orderId,
        String orderNumber,
        Long customerId,
        String status,
        String currency,
        BigDecimal totalAmount,
        int version,
        List<OrderLineBO> lines
) {
    public OrderBO {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public record OrderLineBO(
            Long productId,
            Long quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
    }
}
