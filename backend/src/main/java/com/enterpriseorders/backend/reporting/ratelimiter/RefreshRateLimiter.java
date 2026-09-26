package com.enterpriseorders.backend.reporting.ratelimiter;

import com.enterpriseorders.backend.reporting.port.output.RateLimiterPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory, per-instance rate limiter for materialized view refreshes.
 *
 * <p><b>Per-replica caveat:</b> state is held in process memory, so with N
 * replicas the effective platform-wide rate is one refresh per cooldown per
 * replica (up to N refreshes per cooldown). Acceptable for the POC because
 * refresh is idempotent; a shared limiter (DB table / Redis) would be needed
 * for a strict global rate.</p>
 *
 * <p>Cooldown is configurable via {@code app.reporting.refresh-cooldown-seconds}
 * (default 60) — the code default keeps it working without any yml change.</p>
 */
@Component
public class RefreshRateLimiter implements RateLimiterPort {

    private final long cooldownMs;
    private final AtomicLong lastRefreshTime = new AtomicLong(0);
    /**
     * Timestamp written by the last successful {@link #tryAcquire()} on this
     * thread; used by {@link #refund()} to roll back exactly its own permit.
     */
    private final ThreadLocal<Long> heldPermit = new ThreadLocal<>();

    public RefreshRateLimiter(@Value("${app.reporting.refresh-cooldown-seconds:60}") long cooldownSeconds) {
        this.cooldownMs = cooldownSeconds * 1000;
    }

    @Override
    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long last = lastRefreshTime.get();
        if (now - last < cooldownMs) {
            return false;
        }
        if (!lastRefreshTime.compareAndSet(last, now)) {
            return false;
        }
        heldPermit.set(now);
        return true;
    }

    @Override
    public long remainingCooldownSeconds() {
        long now = System.currentTimeMillis();
        long last = lastRefreshTime.get();
        long elapsed = now - last;
        if (elapsed >= cooldownMs) {
            return 0;
        }
        return (cooldownMs - elapsed) / 1000;
    }

    /**
     * Rolls back this thread's permit after a failed refresh so an operator
     * is never auto-locked out by an error that happened after acquisition.
     *
     * <p>Concurrency tradeoff: the rollback is a compare-and-set on the exact
     * timestamp this thread wrote, so if another thread acquired a permit in
     * the meantime (e.g. the cooldown expired while a slow refresh was still
     * running) the CAS fails and the other caller's cooldown is preserved —
     * in that rare case the failed caller must wait out the cooldown as
     * before. The only residual race is two acquires in the same millisecond,
     * where the CAS may release a permit granted to the other thread; worst
     * case is one extra, harmless (idempotent) MV refresh. No lockout on
     * failure is the priority here.</p>
     */
    @Override
    public void refund() {
        Long held = heldPermit.get();
        heldPermit.remove();
        if (held == null) {
            return;
        }
        lastRefreshTime.compareAndSet(held, held - cooldownMs);
    }
}
