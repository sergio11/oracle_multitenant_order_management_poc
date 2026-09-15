package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.dto.OrderResponse;

public interface ConfirmOrderUseCase {
    OrderResponse confirmOrder(Long orderId, String idempotencyKey);
}
