package com.enterpriseorders.backend.order.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response payload for order operations (SRS §53).
 */
public record OrderResponseDTO(
        Long orderId,
        String orderNumber,
        Long customerId,
        String status,
        String currency,
        BigDecimal totalAmount,
        int version,
        List<OrderLineDTO> lines
) {
    public OrderResponseDTO {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public record OrderLineDTO(
            Long productId,
            Long quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {
    }
}
