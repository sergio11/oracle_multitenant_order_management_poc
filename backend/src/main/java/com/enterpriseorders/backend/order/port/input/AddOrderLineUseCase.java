package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.model.OrderBO;

public interface AddOrderLineUseCase {
    OrderBO addLine(Long orderId, Long productId, Long quantity);
}
