--=====================================================================
-- Security test: VPD cross-tenant isolation (SRS §14, §52, §93, BR-013)
--
-- Verifies TENANT ISOLATION: once a session sets a tenant context,
-- queries can NEVER see rows belonging to another tenant. This is a
-- core defense-in-depth control (VPD + application context).
--
-- Tenant IDs are resolved by BUSINESS CODE (ACME/GLOBEX) - never
-- hardcoded: identity values differ per environment/seed. The test also
-- creates its own orders (one DRAFT order + one line per tenant) so the
-- own-row guards never depend on seed distribution.
--
-- Run as APP_RUNTIME (least-privilege runtime user, see README):
-- Usage:
--   sqlplus app_runtime/<pwd>@//localhost:1521/pdb_oltp \
--     @tests/security/T_security_tenant_isolation.sql
--=====================================================================

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

-- Run as APP_RUNTIME: resolve unqualified names (tables, packages)
-- against the owner schema (no synonyms exist by design).
ALTER SESSION SET CURRENT_SCHEMA = APP_OWNER;

PROMPT === SECURITY: cross-tenant isolation (VPD) ===

-- Resolve the two test tenants by business code. TENANT carries no VPD
-- policy (by design), so this works without context.
VARIABLE v_acme NUMBER
VARIABLE v_globex NUMBER
VARIABLE v_o_acme NUMBER
VARIABLE v_o_globex NUMBER
BEGIN
    SELECT tenant_id INTO :v_acme FROM tenant WHERE tenant_code = 'ACME';
    SELECT tenant_id INTO :v_globex FROM tenant WHERE tenant_code = 'GLOBEX';
    IF :v_acme IS NULL OR :v_globex IS NULL OR :v_acme = :v_globex THEN
        RAISE_APPLICATION_ERROR(-20926, 'Setup: ACME/GLOBEX tenants not found or identical');
    END IF;
    DBMS_OUTPUT.PUT_LINE('Test tenants: ACME=' || :v_acme || ' GLOBEX=' || :v_globex);
END;
/

-- Setup: one DRAFT order + one line per test tenant (via the business
-- packages, as APP_RUNTIME would through the API). No confirm: no stock
-- impact. Cancelled again in the cleanup section.
EXEC pkg_tenant.set_tenant_context(:v_acme);
DECLARE
    v_cust NUMBER;
    v_prod NUMBER;
    v_oid  NUMBER;
BEGIN
    SELECT MIN(customer_id) INTO v_cust FROM customer;  -- VPD: own tenant only
    SELECT MIN(product_id) INTO v_prod FROM product;
    v_oid := pkg_order_management.create_order(:v_acme, v_cust, 'USD');
    -- p_warehouse_id is accepted but ignored (resolved at confirm time).
    pkg_order_management.add_order_line(:v_acme, v_oid, v_prod, 1, NULL);
    :v_o_acme := v_oid;
    DBMS_OUTPUT.PUT_LINE('ACME test order=' || v_oid);
END;
/
-- Commit the setup rows NOW: the fail-closed section below issues a
-- safety ROLLBACK that would otherwise wipe them (WHENEVER SQLERROR
-- EXIT would then roll back the cancel too - hence COMMIT after cleanup).
COMMIT;
EXEC pkg_tenant.set_tenant_context(:v_globex);
DECLARE
    v_cust NUMBER;
    v_prod NUMBER;
    v_oid  NUMBER;
BEGIN
    SELECT MIN(customer_id) INTO v_cust FROM customer;  -- VPD: own tenant only
    SELECT MIN(product_id) INTO v_prod FROM product;
    v_oid := pkg_order_management.create_order(:v_globex, v_cust, 'USD');
    pkg_order_management.add_order_line(:v_globex, v_oid, v_prod, 1, NULL);
    :v_o_globex := v_oid;
    DBMS_OUTPUT.PUT_LINE('GLOBEX test order=' || v_oid);
END;
/
COMMIT;

-- Switch to ACME and count its customers
EXEC pkg_tenant.set_tenant_context(:v_acme);
DECLARE
    c1 NUMBER;
    c2 NUMBER;
BEGIN
    SELECT COUNT(*) INTO c1 FROM customer;             -- ACME customers
    DBMS_OUTPUT.PUT_LINE('ACME customer count = ' || c1);

    -- Positive assertion: ACME MUST see its own rows, otherwise
    -- the isolation checks below would pass vacuously (false pass).
    IF c1 = 0 THEN
        RAISE_APPLICATION_ERROR(-20913,
            'False pass: ACME sees 0 customers (seed not applied or VPD over-filtering)');
    END IF;

    -- Try to touch GLOBEX customers explicitly by id; VPD must block it.
    SELECT COUNT(*) INTO c2
    FROM customer WHERE tenant_id = :v_globex;         -- should be 0 rows
    DBMS_OUTPUT.PUT_LINE('ACME visible rows of GLOBEX = ' || c2);

    IF c2 > 0 THEN
        RAISE_APPLICATION_ERROR(-20910, 'Cross-tenant leak: ACME saw GLOBEX customer rows!');
    END IF;
END;
/

-- Switch to GLOBEX and verify isolation in the other direction
EXEC pkg_tenant.set_tenant_context(:v_globex);
DECLARE
    c1 NUMBER;
    c2 NUMBER;
BEGIN
    SELECT COUNT(*) INTO c1 FROM customer WHERE tenant_id = :v_acme;
    DBMS_OUTPUT.PUT_LINE('GLOBEX visible rows of ACME = ' || c1);
    IF c1 > 0 THEN
        RAISE_APPLICATION_ERROR(-20911, 'Cross-tenant leak: GLOBEX saw ACME customer rows!');
    END IF;

    -- Positive assertion for GLOBEX (prevents false pass)
    SELECT COUNT(*) INTO c2 FROM customer WHERE tenant_id = :v_globex;
    DBMS_OUTPUT.PUT_LINE('GLOBEX own customer count = ' || c2);
    IF c2 = 0 THEN
        RAISE_APPLICATION_ERROR(-20915,
            'False pass: GLOBEX sees 0 customers (seed not applied or VPD over-filtering)');
    END IF;
END;
/

-- Cross-tenant reads on order/payment/inventory tables, direction A:
-- ACME session must see zero rows of GLOBEX (SRS 14, BR-013)
EXEC pkg_tenant.set_tenant_context(:v_acme);
DECLARE
    v_own   NUMBER;
    v_cross NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_own FROM sales_order;
    SELECT COUNT(*) INTO v_cross FROM sales_order WHERE tenant_id = :v_globex;
    DBMS_OUTPUT.PUT_LINE('ACME sales_order own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        RAISE_APPLICATION_ERROR(-20917,
            'False pass: ACME sees 0 sales_order rows (setup order missing?)');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: ACME saw GLOBEX sales_order rows!');
    END IF;

    SELECT COUNT(*) INTO v_own FROM sales_order_line;
    SELECT COUNT(*) INTO v_cross FROM sales_order_line WHERE tenant_id = :v_globex;
    DBMS_OUTPUT.PUT_LINE('ACME sales_order_line own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        RAISE_APPLICATION_ERROR(-20917,
            'False pass: ACME sees 0 sales_order_line rows (setup line missing?)');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: ACME saw GLOBEX sales_order_line rows!');
    END IF;

    SELECT COUNT(*) INTO v_own FROM inventory;
    SELECT COUNT(*) INTO v_cross FROM inventory WHERE tenant_id = :v_globex;
    DBMS_OUTPUT.PUT_LINE('ACME inventory own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        RAISE_APPLICATION_ERROR(-20917,
            'False pass: ACME sees 0 inventory rows (seed not applied?)');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: ACME saw GLOBEX inventory rows!');
    END IF;

    -- Payment may have no rows for ACME: a zero own-count is expected, not
    -- a false pass - the cross-tenant assertion below still must hold.
    SELECT COUNT(*) INTO v_own FROM payment;
    SELECT COUNT(*) INTO v_cross FROM payment WHERE tenant_id = :v_globex;
    DBMS_OUTPUT.PUT_LINE('ACME payment own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        DBMS_OUTPUT.PUT_LINE('NOTE: ACME has no payment rows - leak check is vacuous for payment here');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: ACME saw GLOBEX payment rows!');
    END IF;
END;
/

-- Same checks in the other direction (GLOBEX must see zero of ACME)
EXEC pkg_tenant.set_tenant_context(:v_globex);
DECLARE
    v_own   NUMBER;
    v_cross NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_own FROM sales_order;
    SELECT COUNT(*) INTO v_cross FROM sales_order WHERE tenant_id = :v_acme;
    DBMS_OUTPUT.PUT_LINE('GLOBEX sales_order own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        RAISE_APPLICATION_ERROR(-20917,
            'False pass: GLOBEX sees 0 sales_order rows (setup order missing?)');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: GLOBEX saw ACME sales_order rows!');
    END IF;

    SELECT COUNT(*) INTO v_own FROM sales_order_line;
    SELECT COUNT(*) INTO v_cross FROM sales_order_line WHERE tenant_id = :v_acme;
    DBMS_OUTPUT.PUT_LINE('GLOBEX sales_order_line own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        RAISE_APPLICATION_ERROR(-20917,
            'False pass: GLOBEX sees 0 sales_order_line rows (setup line missing?)');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: GLOBEX saw ACME sales_order_line rows!');
    END IF;

    SELECT COUNT(*) INTO v_own FROM inventory;
    SELECT COUNT(*) INTO v_cross FROM inventory WHERE tenant_id = :v_acme;
    DBMS_OUTPUT.PUT_LINE('GLOBEX inventory own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        RAISE_APPLICATION_ERROR(-20917,
            'False pass: GLOBEX sees 0 inventory rows (seed not applied?)');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: GLOBEX saw ACME inventory rows!');
    END IF;

    SELECT COUNT(*) INTO v_own FROM payment;
    SELECT COUNT(*) INTO v_cross FROM payment WHERE tenant_id = :v_acme;
    DBMS_OUTPUT.PUT_LINE('GLOBEX payment own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        DBMS_OUTPUT.PUT_LINE('NOTE: GLOBEX has no payment rows - leak check is vacuous for payment here');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: GLOBEX saw ACME payment rows!');
    END IF;
END;
/

-- Without any context (fail-closed, SRS 13): zero rows everywhere
EXEC pkg_tenant.clear_tenant_context;
DECLARE
    c1 NUMBER;
BEGIN
    SELECT COUNT(*) INTO c1 FROM customer;
    DBMS_OUTPUT.PUT_LINE('No-context customer query returned = ' || c1);
    IF c1 > 0 THEN
        RAISE_APPLICATION_ERROR(-20912, 'Fail-closed violated: rows visible with no tenant context!');
    END IF;
END;
/

-- INSERT without tenant context must fail (VPD predicate 1=0 with
-- update_check => TRUE -> ORA-28104, or ORA-01031 if the direct INSERT
-- privilege is missing; both are acceptable rejections here).
-- NOTE: dynamic SQL on purpose - with static SQL a missing INSERT
-- privilege is a COMPILE-time error (ORA-06550+01031) that the block's
-- own handler cannot catch; via EXECUTE IMMEDIATE it becomes a
-- runtime error the handler sees.
DECLARE
    v_inserted BOOLEAN := FALSE;
BEGIN
    BEGIN
        EXECUTE IMMEDIATE
            'INSERT INTO customer (tenant_id, customer_number, first_name, last_name, email, status)'
            || ' VALUES (1, ''VPD-NOCtx-TEST'', ''No'', ''Context'', ''vpd-noctx-test@example.com'', ''ACTIVE'')';
        v_inserted := TRUE;
    EXCEPTION
        WHEN OTHERS THEN
            DBMS_OUTPUT.PUT_LINE('Insert without context rejected as expected: ' || SQLERRM);
    END;

    IF v_inserted THEN
        ROLLBACK;
        RAISE_APPLICATION_ERROR(-20914, 'VPD allowed INSERT with no tenant context!');
    END IF;
END;
/

-- UPDATE / DELETE without tenant context must touch 0 rows (fail-closed
-- VPD predicate 1=0) or be rejected outright (ORA-28104 / ORA-01031 when
-- the direct DML privilege is missing - both are acceptable rejections).
-- NOTE: dynamic SQL on purpose (same reason as the INSERT case above);
-- anything staged by a leaked write is rolled back before failing.
DECLARE
    v_rows NUMBER := 0;
BEGIN
    BEGIN
        EXECUTE IMMEDIATE 'UPDATE customer SET first_name = ''VPD'' WHERE tenant_id = 1';
        v_rows := SQL%ROWCOUNT;
    EXCEPTION
        WHEN OTHERS THEN
            DBMS_OUTPUT.PUT_LINE('Update without context rejected as expected: ' || SQLERRM);
            v_rows := 0;
    END;
    IF v_rows > 0 THEN
        ROLLBACK;
        RAISE_APPLICATION_ERROR(-20918, 'Fail-closed violated: UPDATE with no context touched rows!');
    END IF;

    BEGIN
        EXECUTE IMMEDIATE 'DELETE FROM customer WHERE tenant_id = 1';
        v_rows := SQL%ROWCOUNT;
    EXCEPTION
        WHEN OTHERS THEN
            DBMS_OUTPUT.PUT_LINE('Delete without context rejected as expected: ' || SQLERRM);
            v_rows := 0;
    END;
    IF v_rows > 0 THEN
        ROLLBACK;
        RAISE_APPLICATION_ERROR(-20918, 'Fail-closed violated: DELETE with no context touched rows!');
    END IF;

    ROLLBACK; -- safety: discard anything the statements may have staged
    DBMS_OUTPUT.PUT_LINE('No-context UPDATE/DELETE touched 0 rows as expected');
END;
/

-- Least-privilege assertions (SRS 58/59, C2/L3): the runtime user must
-- not bypass VPD and must hold no DELETE on business tables.
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_SYS_PRIVS WHERE PRIVILEGE = 'EXEMPT ACCESS POLICY';
    DBMS_OUTPUT.PUT_LINE('EXEMPT ACCESS POLICY grants = ' || v_cnt);
    IF v_cnt > 0 THEN
        RAISE_APPLICATION_ERROR(-20919,
            'Privilege violation: APP_RUNTIME holds EXEMPT ACCESS POLICY (VPD bypass)!');
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM USER_TAB_PRIVS
     WHERE TABLE_NAME IN ('SALES_ORDER','SALES_ORDER_LINE','PAYMENT','INVENTORY')
       AND PRIVILEGE = 'DELETE';
    DBMS_OUTPUT.PUT_LINE('DELETE grants on business tables = ' || v_cnt);
    IF v_cnt > 0 THEN
        RAISE_APPLICATION_ERROR(-20919,
            'Privilege violation: APP_RUNTIME holds DELETE on business tables!');
    END IF;
END;
/

-- Cleanup: cancel the setup orders (per-tenant context), then reset the
-- context for the shared pool. Cancelled orders keep stock untouched
-- (setup orders were never confirmed).
EXEC pkg_tenant.set_tenant_context(:v_acme);
BEGIN
    pkg_order_management.cancel_order(:v_acme, :v_o_acme, 'SECURITY-TEST-CLEANUP');
    DBMS_OUTPUT.PUT_LINE('ACME test order cancelled');
END;
/
EXEC pkg_tenant.set_tenant_context(:v_globex);
BEGIN
    pkg_order_management.cancel_order(:v_globex, :v_o_globex, 'SECURITY-TEST-CLEANUP');
    DBMS_OUTPUT.PUT_LINE('GLOBEX test order cancelled');
END;
/
-- Persist the cancels so a mid-suite abort/exit-rollback cannot leave
-- setup rows behind as live DRAFTs.
COMMIT;

-- Clean up: reset context for the shared pool
EXEC pkg_tenant.clear_tenant_context;

PROMPT === SECURITY: tenant isolation PASSED ===
EXIT SUCCESS
