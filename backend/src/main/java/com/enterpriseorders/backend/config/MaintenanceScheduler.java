package com.enterpriseorders.backend.config;

import com.enterpriseorders.backend.order.port.output.IdempotencyStore;
import com.enterpriseorders.backend.outbox.port.output.OutboxRelayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintenance jobs (disabled by default; enabled explicitly per environment).
 *
 * <p>Both jobs are cross-tenant administrative operations and therefore go
 * through definer's-rights PL/SQL packages, not through VPD-filtered DML:
 * <ul>
 *   <li>outbox relay: NEW -> PROCESSING -> PUBLISHED with SKIP LOCKED;</li>
 *   <li>idempotency retention: purge expired COMPLETED keys and stale
 *       PENDING rows (a crashed request must not block retries forever).</li>
 * </ul>
 *
 * <p>Neither job sets APP_CTX: the scheduler thread has no request tenant,
 * and the packages deliberately act as the owner. This mirrors
 * refresh_reporting_views.
 */
@Component
@ConditionalOnProperty(name = "app.maintenance.enabled", havingValue = "true")
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private final OutboxRelayPort outboxRelay;
    private final IdempotencyStore idempotencyStore;
    private final int batchSize;
    private final int retentionDays;

    public MaintenanceScheduler(OutboxRelayPort outboxRelay,
                                IdempotencyStore idempotencyStore,
                                @Value("${app.maintenance.outbox-batch-size:100}") int batchSize,
                                @Value("${app.idempotency.retention-days:30}") int retentionDays) {
        this.outboxRelay = outboxRelay;
        this.idempotencyStore = idempotencyStore;
        this.batchSize = batchSize;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelayString = "${app.maintenance.outbox-interval-ms:30000}",
            initialDelayString = "${app.maintenance.initial-delay-ms:20000}")
    @Transactional
    public void relayOutbox() {
        try {
            int published = outboxRelay.relayOnce(batchSize);
            if (published > 0) {
                log.info("Outbox relay published {} event(s)", published);
            }
        } catch (RuntimeException ex) {
            // Never let a transient DB failure cancel the schedule.
            log.error("Outbox relay batch failed; events stay NEW for the next run", ex);
        }
    }

    @Scheduled(fixedDelayString = "${app.maintenance.idempotency-purge-interval-ms:3600000}",
            initialDelayString = "${app.maintenance.initial-delay-ms:60000}")
    @Transactional
    public void purgeIdempotencyKeys() {
        try {
            int deleted = idempotencyStore.purgeExpired(retentionDays);
            if (deleted > 0) {
                log.info("Purged {} expired idempotency key(s) (retention {} days)",
                        deleted, retentionDays);
            }
        } catch (RuntimeException ex) {
            log.error("Idempotency retention purge failed", ex);
        }
    }
}
