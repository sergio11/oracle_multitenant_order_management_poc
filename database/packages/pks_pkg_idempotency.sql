--=====================================================================
-- PKG_IDEMPOTENCY - Package specification
--
-- Retention/cleanup for IDEMPOTENCY_KEY.
--
-- The idempotency table is an unbounded log by construction: one row per
-- idempotent operation, forever. Two problems follow:
--   1. table growth with no retention policy;
--   2. a COMPLETED row replays a historical response indefinitely, even
--      after the aggregate moved on (a confirm replay would still report
--      CONFIRMED for an order that is now CANCELLED).
--
-- This package owns the cleanup half; the backend owns the replay-window
-- check (IDEMPOTENCY_KEY_EXPIRED) and uses the same retention setting.
--
-- DEFINER's rights: the purge is a cross-tenant maintenance operation.
-- A direct DELETE as APP_RUNTIME would be filtered by VPD and delete
-- nothing (the shared-schema isolation working as designed).
--=====================================================================
CREATE OR REPLACE PACKAGE pkg_idempotency AS

    -- Deletes finished records older than p_retention_days and stale
    -- PENDING rows older than one day (a PENDING row that survived its
    -- transaction is a crashed request and must not block retries).
    -- Returns the total number of deleted rows.
    FUNCTION purge_expired(p_retention_days IN NUMBER DEFAULT 30) RETURN NUMBER;

END pkg_idempotency;
/
