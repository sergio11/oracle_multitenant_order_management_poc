package com.enterpriseorders.backend.order.port.input;

import com.enterpriseorders.backend.order.model.OrderView;

public interface AddOrderLineUseCase {
    OrderView addLine(Long orderId, Long productId, Long quantity);
}
