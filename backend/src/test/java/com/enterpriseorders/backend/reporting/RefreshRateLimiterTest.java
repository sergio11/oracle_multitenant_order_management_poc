package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.reporting.ratelimiter.RefreshRateLimiter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefreshRateLimiterTest {
    private final RefreshRateLimiter limiter = new RefreshRateLimiter(60);

    @Test
    void shouldAllowFirstRefresh() {
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void shouldDenyRefreshWithinCooldown() {
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void shouldReturnNonZeroRemainingWithinCooldown() {
        limiter.tryAcquire();
        long remaining = limiter.remainingCooldownSeconds();
        assertTrue(remaining > 0);
        assertTrue(remaining <= 60);
    }

    @Test
    void shouldReturnZeroRemainingWhenCooldownExpired() {
        // The initial state has lastRefreshTime = 0, so it should be 0
        assertEquals(0, limiter.remainingCooldownSeconds());
    }

    @Test
    void shouldHonorConfigurableCooldown() {
        RefreshRateLimiter custom = new RefreshRateLimiter(300);
        assertTrue(custom.tryAcquire());
        long remaining = custom.remainingCooldownSeconds();
        assertTrue(remaining > 60, "cooldown must come from the constructor, not a hardcoded 60s");
        assertTrue(remaining <= 300);
    }

    @Test
    void shouldAllowImmediateRetryAfterRefund() {
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        limiter.refund();
        assertTrue(limiter.tryAcquire(), "refund must release the permit so a failure does not lock out the operator");
    }

    @Test
    void shouldIgnoreRefundWithoutAcquire() {
        limiter.refund();
        assertEquals(0, limiter.remainingCooldownSeconds());
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void shouldBeIdempotentOnRepeatedRefund() {
        // First refund rolls back this thread's own permit (CAS succeeds);
        // a second refund has no held permit and is a no-op.
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        limiter.refund();
        limiter.refund();
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void shouldIgnoreRefundAfterRelease() {
        // Successful path: acquire -> release clears the thread-bound permit,
        // so a later refund (e.g. from another request on this pooled thread)
        // is a no-op and the cooldown is preserved.
        assertTrue(limiter.tryAcquire());
        limiter.release();
        limiter.refund();
        assertFalse(limiter.tryAcquire());
    }
}
