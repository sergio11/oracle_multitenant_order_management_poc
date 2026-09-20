package com.enterpriseorders.backend.reporting;

import com.enterpriseorders.backend.reporting.infrastructure.RefreshRateLimiter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefreshRateLimiterTest {
    private final RefreshRateLimiter limiter = new RefreshRateLimiter();

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
        long remaining = limiter.getRemainingCooldownMs();
        assertTrue(remaining > 0);
    }

    @Test
    void shouldReturnZeroRemainingWhenCooldownExpired() {
        // The initial state has lastRefreshTime = 0, so it should be 0
        assertEquals(0, limiter.getRemainingCooldownMs());
    }
}
