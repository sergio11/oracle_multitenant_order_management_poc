package com.enterpriseorders.backend.order.infrastructure;

import com.enterpriseorders.backend.order.dto.OrderResponse;
import com.enterpriseorders.backend.order.port.output.IdempotencyStore;
import com.enterpriseorders.backend.order.port.output.OrderRepository;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class IdempotentOperationExecutor {

    private final IdempotencyStore idempotencyStore;
    private final OrderRepository orderRepository;

    public IdempotentOperationExecutor(IdempotencyStore idempotencyStore, OrderRepository orderRepository) {
        this.idempotencyStore = idempotencyStore;
        this.orderRepository = orderRepository;
    }

    public OrderResponse execute(Long tenantId, String idempotencyKey, String operation,
                                 Long aggregateId, Supplier<OrderResponse> action) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyStore.findByKey(tenantId, idempotencyKey);
            if (existing.isPresent()) {
                return orderRepository.findById(tenantId, existing.get().aggregateId());
            }
            if (!idempotencyStore.insertPending(tenantId, idempotencyKey, operation, aggregateId)) {
                var raceResult = idempotencyStore.findByKey(tenantId, idempotencyKey);
                if (raceResult.isPresent()) {
                    return orderRepository.findById(tenantId, raceResult.get().aggregateId());
                }
            }
        }
        return action.get();
    }
}
