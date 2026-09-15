package com.enterpriseorders.backend.order.port.output;

public interface InventoryRepository {
    long getAvailableStock(Long tenantId, Long warehouseId, Long productId);
}
