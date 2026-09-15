package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.dto.OrderResponse;

public interface CancelOrderUseCase {
    OrderResponse cancelOrder(Long orderId, String reason);
}
