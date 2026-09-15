package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.dto.OrderResponse;

public interface AddOrderLineUseCase {
    OrderResponse addLine(Long orderId, Long productId, Long quantity, Long warehouseId);
}
