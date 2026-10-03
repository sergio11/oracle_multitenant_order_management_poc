package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.model.OrderBO;

public interface CancelOrderUseCase {
    OrderBO cancelOrder(Long orderId, String reason, String idempotencyKey);
}
