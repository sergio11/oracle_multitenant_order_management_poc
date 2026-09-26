--=====================================================================
-- V019 - Least-privilege grant corrections (C2, L3)
--
-- V017 (and the grant blocks in database/packages/) granted
-- APP_REPORTING EXECUTE on the mutating business packages. Those
-- packages are DEFINER's rights (owner APP_OWNER), so APP_REPORTING -
-- nominally a read-only user - could confirm/cancel orders, register
-- payments, reserve/release/adjust stock and write audit rows.
--
-- This migration REVOKEs:
--
--   APP_REPORTING (C2):
--     EXECUTE ON pkg_order_management, pkg_inventory, pkg_audit
--     (keeps pkg_tenant for the session tenant context, pkg_vpd and
--      SELECT on all tables/MVs, plus refresh_reporting_views V018)
--
--   APP_RUNTIME (L3):
--     INSERT  ON audit_event, order_status_history
--     INSERT / UPDATE ON outbox_event
--     (every write goes through the definer-rights packages; the only
--      direct DML kept is idempotency_key for IdempotencyDao)
--
-- WHY A SEPARATE MIGRATION: V017 itself was corrected for FRESH
-- installs, but on databases where V017 already ran, editing the
-- file + `flyway repair` only fixes the checksum - the GRANT
-- statements are NOT re-executed. This migration closes that gap.
--
-- Every revoke is wrapped in its own sub-block whose OTHERS handler
-- swallows only SQLCODE -1924/-1927 (privilege not granted / cannot
-- revoke what was never granted), so re-runs are no-ops and any other
-- failure is re-raised loudly.
--=====================================================================
BEGIN
    ------------------------------------------------------------------
    -- C2: APP_REPORTING must stay read-only
    ------------------------------------------------------------------
    -- pkg_order_management
    BEGIN
        EXECUTE IMMEDIATE 'REVOKE EXECUTE ON ' || USER || '.pkg_order_management FROM app_reporting';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE NOT IN (-1924, -1927) THEN RAISE; END IF; -- privilege not held: nothing to revoke
    END;
    -- pkg_inventory
    BEGIN
        EXECUTE IMMEDIATE 'REVOKE EXECUTE ON ' || USER || '.pkg_inventory FROM app_reporting';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE NOT IN (-1924, -1927) THEN RAISE; END IF;
    END;
    -- pkg_audit
    BEGIN
        EXECUTE IMMEDIATE 'REVOKE EXECUTE ON ' || USER || '.pkg_audit FROM app_reporting';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE NOT IN (-1924, -1927) THEN RAISE; END IF;
    END;

    ------------------------------------------------------------------
    -- L3: APP_RUNTIME writes business data only via definer-rights
    -- packages; the direct parity DML grants must go.
    ------------------------------------------------------------------
    BEGIN
        EXECUTE IMMEDIATE 'REVOKE INSERT ON ' || USER || '.audit_event FROM app_runtime';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE NOT IN (-1924, -1927) THEN RAISE; END IF;
    END;
    BEGIN
        EXECUTE IMMEDIATE 'REVOKE INSERT ON ' || USER || '.order_status_history FROM app_runtime';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE NOT IN (-1924, -1927) THEN RAISE; END IF;
    END;
    BEGIN
        EXECUTE IMMEDIATE 'REVOKE INSERT ON ' || USER || '.outbox_event FROM app_runtime';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE NOT IN (-1924, -1927) THEN RAISE; END IF;
    END;
    BEGIN
        EXECUTE IMMEDIATE 'REVOKE UPDATE ON ' || USER || '.outbox_event FROM app_runtime';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE NOT IN (-1924, -1927) THEN RAISE; END IF;
    END;
END;
/
