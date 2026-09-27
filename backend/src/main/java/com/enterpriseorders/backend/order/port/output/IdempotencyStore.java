package com.enterpriseorders.backend.order.port.output;

import java.util.Optional;

public interface IdempotencyStore {
    Optional<IdempotencyRecord> findByKey(Long tenantId, String idempotencyKey);
    boolean insertPending(Long tenantId, String idempotencyKey, String operation,
                          Long aggregateId, String requestFingerprint);

    /**
     * Flips the record to COMPLETED, backfills the real aggregate id and
     * stores the serialized response. The aggregate backfill matters for
     * CREATE_ORDER: the PENDING row carries the 0 sentinel (the order does
     * not exist yet), so completion must persist the created order's id —
     * otherwise a later payload-less replay cannot fall back to findById.
     * Returns the number of updated rows; callers must treat anything
     * other than 1 as a failure (never a silent success).
     */
    int markCompleted(Long tenantId, String idempotencyKey, Long aggregateId, String responsePayload);

    /**
     * Binds a key to operation+aggregate+request. {@code requestFingerprint}
     * is the caller's digest of the canonical request parameters (V024):
     * a NULL stored value means "legacy row, skip the check".
     */
    record IdempotencyRecord(Long aggregateId, String operation, String status,
                             String responsePayload, String requestFingerprint) {}
}
