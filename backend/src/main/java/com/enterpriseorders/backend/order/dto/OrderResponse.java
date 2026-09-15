package com.enterpriseorders.backend.order.dto;

import java.math.BigDecimal;

/**
 * Response payload for order operations (SRS §53).
 */
public record OrderResponse(
        Long orderId,
        String orderNumber,
        Long tenantId,
        Long customerId,
        String status,
        String currency,
        BigDecimal totalAmount,
        int version
) {
}
