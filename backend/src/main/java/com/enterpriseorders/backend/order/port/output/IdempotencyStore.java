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
     *
     * <p>{@code createdAt} feeds the replay window: a COMPLETED row older
     * than {@code app.idempotency.retention-days} is no longer replayed
     * (the stored response is a historical snapshot, not current truth).
     * The five-argument constructor is kept for callers/tests that do not
     * care about the timestamp (null = unknown, treated as not expired).
     */
    record IdempotencyRecord(Long aggregateId, String operation, String status,
                             String responsePayload, String requestFingerprint,
                             java.time.Instant createdAt) {

        public IdempotencyRecord(Long aggregateId, String operation, String status,
                                 String responsePayload, String requestFingerprint) {
            this(aggregateId, operation, status, responsePayload, requestFingerprint, null);
        }
    }

    /**
     * Retention job: deletes expired COMPLETED rows and stale PENDING rows
     * (a PENDING row that outlived its transaction is a crashed request and
     * would otherwise block retries forever). Returns deleted row count.
     */
    int purgeExpired(int retentionDays);
}
