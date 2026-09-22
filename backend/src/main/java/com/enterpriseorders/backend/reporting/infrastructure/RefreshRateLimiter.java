package com.enterpriseorders.backend.reporting.infrastructure;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class RefreshRateLimiter {

    private final AtomicLong lastRefreshTime = new AtomicLong(0);
    private static final long COOLDOWN_MS = 60_000;

    public boolean canRefresh() {
        long now = System.currentTimeMillis();
        long lastRefresh = lastRefreshTime.get();
        return (now - lastRefresh) >= COOLDOWN_MS;
    }

    public long getRemainingCooldownSeconds() {
        long now = System.currentTimeMillis();
        long lastRefresh = lastRefreshTime.get();
        long elapsed = now - lastRefresh;
        if (elapsed >= COOLDOWN_MS) {
            return 0;
        }
        return (COOLDOWN_MS - elapsed) / 1000;
    }

    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long last = lastRefreshTime.get();
        if (now - last < COOLDOWN_MS) return false;
        return lastRefreshTime.compareAndSet(last, now);
    }
}
