package com.enterpriseorders.backend.outbox.port.output;

/**
 * Driven port for the outbox relay side (the write side lives in PL/SQL,
 * inside the business transaction).
 *
 * <p>The relay is a cross-tenant maintenance operation: it must see every
 * tenant's NEW events regardless of the request context, so the adapter
 * delegates to a definer's-rights package.
 */
public interface OutboxRelayPort {

    /**
     * Claims and publishes up to {@code limit} NEW events.
     *
     * @return number of events transitioned to PUBLISHED in this batch
     */
    int relayOnce(int limit);
}
