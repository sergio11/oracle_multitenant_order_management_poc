package com.enterpriseorders.backend.reporting.port.output;

/**
 * Output port for the refresh rate-limit policy.
 *
 * Keeps the application service decoupled from the concrete limiter
 * implementation (DIP): the service depends on this port only, the
 * in-memory implementation lives in {@code reporting.ratelimiter}.
 */
public interface RateLimiterPort {

    /**
     * Attempts to acquire the single refresh permit.
     *
     * @return {@code true} if the permit was granted, {@code false} if the
     *         caller must still wait for the cooldown to expire.
     */
    boolean tryAcquire();

    /**
     * Remaining cooldown in whole seconds before a new permit can be
     * acquired; {@code 0} when the limiter is idle.
     */
    long remainingCooldownSeconds();

    /**
     * Gives the permit back when the operation it was taken for failed, so
     * the operator is not locked out for a full cooldown after an error.
     * See the implementation javadoc for the concurrency tradeoff.
     */
    void refund();

    /**
     * Releases a successfully used permit. Clears any thread-bound permit
     * state so pooled worker threads never retain stale limiter state
     * after a completed refresh.
     */
    void release();
}
