--=====================================================================
-- PKG_OUTBOX - Package specification
--
-- Relay side of the transactional outbox (SRS 28, 63).
--
-- The write side is the business transaction itself (pkg_order_management
-- inserts the event in the same transaction). This package closes the
-- pattern: it claims NEW events with FOR UPDATE SKIP LOCKED and drives
-- them through NEW -> PROCESSING -> PUBLISHED.
--
-- DEFINER's rights on purpose: the relay is a cross-tenant administrative
-- operation (it must see every tenant's events regardless of the current
-- APP_CTX tenant). The owner holds EXEMPT ACCESS POLICY, so the VPD
-- predicate does not hide other tenants from the relay. This is the same
-- model used by refresh_reporting_views.
--=====================================================================
CREATE OR REPLACE PACKAGE pkg_outbox AS

    -- Claims and publishes up to p_limit NEW events.
    -- Returns the number of events transitioned to PUBLISHED in this batch.
    -- Safe for concurrent callers: SKIP LOCKED makes each relay skip rows
    -- another relay already owns instead of blocking on them.
    FUNCTION relay_once(p_limit IN NUMBER DEFAULT 100) RETURN NUMBER;

END pkg_outbox;
/
