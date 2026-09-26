package com.enterpriseorders.backend.order.port.output;

import java.util.Optional;

public interface IdempotencyStore {
    Optional<IdempotencyRecord> findByKey(Long tenantId, String idempotencyKey);
    boolean insertPending(Long tenantId, String idempotencyKey, String operation, Long aggregateId);

    /**
     * Flips the record to COMPLETED and stores the serialized response.
     * Returns the number of updated rows; callers must treat anything
     * other than 1 as a failure (never a silent success).
     */
    int markCompleted(Long tenantId, String idempotencyKey, String responsePayload);

    record IdempotencyRecord(Long aggregateId, String operation, String status, String responsePayload) {}
}
