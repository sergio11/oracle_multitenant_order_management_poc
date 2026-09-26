package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.model.OrderView;

public interface CancelOrderUseCase {
    OrderView cancelOrder(Long orderId, String reason, String idempotencyKey);
}
