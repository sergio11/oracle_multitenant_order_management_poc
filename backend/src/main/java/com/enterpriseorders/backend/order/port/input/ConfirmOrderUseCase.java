package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.model.OrderView;

public interface ConfirmOrderUseCase {
    OrderView confirmOrder(Long orderId, String idempotencyKey);
}
