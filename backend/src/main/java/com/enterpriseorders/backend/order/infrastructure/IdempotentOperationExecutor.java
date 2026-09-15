package com.enterpriseorders.backend.order.infrastructure;

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

    public <T> T execute(Long tenantId, String idempotencyKey, String operation,
                         Long aggregateId, Supplier<T> action) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = idempotencyStore.findByKey(tenantId, idempotencyKey);
            if (existing.isPresent()) {
                @SuppressWarnings("unchecked")
                T result = (T) orderRepository.findById(tenantId, existing.get().aggregateId());
                return result;
            }
            if (!idempotencyStore.insertPending(tenantId, idempotencyKey, operation, aggregateId)) {
                var raceResult = idempotencyStore.findByKey(tenantId, idempotencyKey);
                if (raceResult.isPresent()) {
                    @SuppressWarnings("unchecked")
                    T result = (T) orderRepository.findById(tenantId, raceResult.get().aggregateId());
                    return result;
                }
            }
        }
        return action.get();
    }
}
