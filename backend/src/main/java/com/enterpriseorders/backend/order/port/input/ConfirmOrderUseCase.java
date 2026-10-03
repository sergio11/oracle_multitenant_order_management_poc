package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.model.OrderBO;

public interface ConfirmOrderUseCase {
    OrderBO confirmOrder(Long orderId, String idempotencyKey);
}
