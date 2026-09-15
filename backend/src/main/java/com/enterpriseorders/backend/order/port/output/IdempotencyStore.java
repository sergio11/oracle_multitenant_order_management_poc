package com.enterpriseorders.backend.order.port.output;

import java.util.Optional;

public interface IdempotencyStore {
    Optional<IdempotencyRecord> findByKey(Long tenantId, String idempotencyKey);
    boolean insertPending(Long tenantId, String idempotencyKey, String operation, Long aggregateId);
    void markCompleted(Long tenantId, String idempotencyKey, String responsePayload);

    record IdempotencyRecord(Long aggregateId, String operation, String status) {}
}
