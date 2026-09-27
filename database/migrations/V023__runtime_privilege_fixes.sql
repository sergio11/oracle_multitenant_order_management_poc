--=====================================================================
-- V023 - Runtime least-privilege corrections (closure fixes C2, C3)
--
-- (1) APP_RUNTIME loses direct EXECUTE on pkg_inventory. Only the test
-- helper `set_test_stock` was ever called directly as APP_RUNTIME
-- (tests/concurrency/C_setup_stock.sql); all production stock movement
-- is internal to the definer-rights pkg_order_management calls
-- (reserve_stock / release_stock in V010 packages). Removing this grant
-- closes the test backdoor that let any runtime session force
-- available_quantity and zero reserved_quantity of CONFIRMED orders.
-- The concurrency suite setup now runs as APP_OWNER (owner change in
-- the runner, no package call change).
--
-- (2) APP_REPORTING loses EXECUTE on pkg_tenant and pkg_vpd. pkg_tenant
-- is a lifecycle + session-context package: keeping EXECUTE let the
-- nominally read-only reporting user create/activate/suspend ANY
-- tenant (TENANT has no VPD policy). pkg_tenant.set_tenant_context is
-- currently called only by APP_RUNTIME sessions (single datasource in
-- the backend); APP_REPORTING has no callers, so nothing breaks.
-- pkg_vpd is revoked as unnecessary surface (RLS policy functions
-- execute as pf_owner APP_OWNER, never as the grantee).
--
-- WHY A SEPARATE MIGRATION (same as V019): the grant sources
-- (V017, deploy_packages.sql, pkb_pkg_tenant.sql) are corrected for
-- FRESH installs, but on databases where V017 already ran, editing
-- files only fixes checksums - the GRANTs are NOT re-executed.
--
-- Every revoke tolerates only SQLCODE -1924/-1927 (privilege not
-- held); any other failure is re-raised loudly.
--=====================================================================
BEGIN
    ------------------------------------------------------------------
    -- C2: APP_RUNTIME must not reach the pkg_inventory test backdoor
    ------------------------------------------------------------------
    BEGIN
        EXECUTE IMMEDIATE 'REVOKE EXECUTE ON ' || USER || '.pkg_inventory FROM app_runtime';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE NOT IN (-1924, -1927) THEN RAISE; END IF; -- privilege not held: nothing to revoke
    END;

    ------------------------------------------------------------------
    -- C3: APP_REPORTING is read-only - no tenant lifecycle, no context
    -- manipulation, no predicate surface.
    ------------------------------------------------------------------
    -- pkg_tenant (create/activate/suspend tenant + session context)
    BEGIN
        EXECUTE IMMEDIATE 'REVOKE EXECUTE ON ' || USER || '.pkg_tenant FROM app_reporting';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE NOT IN (-1924, -1927) THEN RAISE; END IF;
    END;
    -- pkg_vpd (RLS predicate holder; executes as pf_owner, never callee)
    BEGIN
        EXECUTE IMMEDIATE 'REVOKE EXECUTE ON ' || USER || '.pkg_vpd FROM app_reporting';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE NOT IN (-1924, -1927) THEN RAISE; END IF;
    END;
END;
/
