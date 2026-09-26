--=====================================================================
-- Security test: VPD cross-tenant isolation (SRS §14, §52, §93, BR-013)
--
-- Verifies TENANT ISOLATION: once a session sets a tenant context,
-- queries can NEVER see rows belonging to another tenant. This is a
-- core defense-in-depth control (VPD + application context).
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

-- Switch to tenant 1 and count its customers
EXEC pkg_tenant.set_tenant_context(1);
DECLARE
    c1 NUMBER;
    c2 NUMBER;
BEGIN
    SELECT COUNT(*) INTO c1 FROM customer;             -- tenant 1 customers
    DBMS_OUTPUT.PUT_LINE('Tenant 1 customer count = ' || c1);

    -- Positive assertion: tenant 1 MUST see its own rows, otherwise
    -- the isolation checks below would pass vacuously (false pass).
    IF c1 = 0 THEN
        RAISE_APPLICATION_ERROR(-20913,
            'False pass: tenant 1 sees 0 customers (seed not applied or VPD over-filtering)');
    END IF;

    -- Try to touch tenant 2 customer explicitly by id; VPD must block it.
    SELECT COUNT(*) INTO c2
    FROM customer WHERE tenant_id = 2;                 -- should be 0 rows
    DBMS_OUTPUT.PUT_LINE('Tenant 1 visible rows for tenant_id=2 = ' || c2);

    IF c2 > 0 THEN
        RAISE_APPLICATION_ERROR(-20910, 'Cross-tenant leak: tenant 1 saw tenant 2 customer rows!');
    END IF;
END;
/

-- Switch to tenant 2 and verify isolation in the other direction
EXEC pkg_tenant.set_tenant_context(2);
DECLARE
    c1 NUMBER;
    c2 NUMBER;
BEGIN
    SELECT COUNT(*) INTO c1 FROM customer WHERE tenant_id = 1;
    DBMS_OUTPUT.PUT_LINE('Tenant 2 visible rows for tenant_id=1 = ' || c1);
    IF c1 > 0 THEN
        RAISE_APPLICATION_ERROR(-20911, 'Cross-tenant leak: tenant 2 saw tenant 1 customer rows!');
    END IF;

    -- Positive assertion for tenant 2 (prevents false pass)
    SELECT COUNT(*) INTO c2 FROM customer WHERE tenant_id = 2;
    DBMS_OUTPUT.PUT_LINE('Tenant 2 own customer count = ' || c2);
    IF c2 = 0 THEN
        RAISE_APPLICATION_ERROR(-20915,
            'False pass: tenant 2 sees 0 customers (seed not applied or VPD over-filtering)');
    END IF;
END;
/

-- Cross-tenant reads on order/payment/inventory tables, direction A:
-- tenant 1 session must see zero rows of tenant 2 (SRS 14, BR-013)
EXEC pkg_tenant.set_tenant_context(1);
DECLARE
    v_own   NUMBER;
    v_cross NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_own FROM sales_order;
    SELECT COUNT(*) INTO v_cross FROM sales_order WHERE tenant_id = 2;
    DBMS_OUTPUT.PUT_LINE('T1 sales_order own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        RAISE_APPLICATION_ERROR(-20917,
            'False pass: tenant 1 sees 0 sales_order rows (seed not applied?)');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: tenant 1 saw tenant 2 sales_order rows!');
    END IF;

    SELECT COUNT(*) INTO v_own FROM sales_order_line;
    SELECT COUNT(*) INTO v_cross FROM sales_order_line WHERE tenant_id = 2;
    DBMS_OUTPUT.PUT_LINE('T1 sales_order_line own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        RAISE_APPLICATION_ERROR(-20917,
            'False pass: tenant 1 sees 0 sales_order_line rows (seed not applied?)');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: tenant 1 saw tenant 2 sales_order_line rows!');
    END IF;

    SELECT COUNT(*) INTO v_own FROM inventory;
    SELECT COUNT(*) INTO v_cross FROM inventory WHERE tenant_id = 2;
    DBMS_OUTPUT.PUT_LINE('T1 inventory own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        RAISE_APPLICATION_ERROR(-20917,
            'False pass: tenant 1 sees 0 inventory rows (seed not applied?)');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: tenant 1 saw tenant 2 inventory rows!');
    END IF;

    -- Payment has no seed rows: a zero own-count is expected, not a
    -- false pass - the cross-tenant assertion below still must hold.
    SELECT COUNT(*) INTO v_own FROM payment;
    SELECT COUNT(*) INTO v_cross FROM payment WHERE tenant_id = 2;
    DBMS_OUTPUT.PUT_LINE('T1 payment own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        DBMS_OUTPUT.PUT_LINE('NOTE: tenant 1 has no payment rows - leak check is vacuous for payment here');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: tenant 1 saw tenant 2 payment rows!');
    END IF;
END;
/

-- Same checks in the other direction (tenant 2 must see zero of tenant 1)
EXEC pkg_tenant.set_tenant_context(2);
DECLARE
    v_own   NUMBER;
    v_cross NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_own FROM sales_order;
    SELECT COUNT(*) INTO v_cross FROM sales_order WHERE tenant_id = 1;
    DBMS_OUTPUT.PUT_LINE('T2 sales_order own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        RAISE_APPLICATION_ERROR(-20917,
            'False pass: tenant 2 sees 0 sales_order rows (seed not applied?)');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: tenant 2 saw tenant 1 sales_order rows!');
    END IF;

    SELECT COUNT(*) INTO v_own FROM sales_order_line;
    SELECT COUNT(*) INTO v_cross FROM sales_order_line WHERE tenant_id = 1;
    DBMS_OUTPUT.PUT_LINE('T2 sales_order_line own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        RAISE_APPLICATION_ERROR(-20917,
            'False pass: tenant 2 sees 0 sales_order_line rows (seed not applied?)');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: tenant 2 saw tenant 1 sales_order_line rows!');
    END IF;

    SELECT COUNT(*) INTO v_own FROM inventory;
    SELECT COUNT(*) INTO v_cross FROM inventory WHERE tenant_id = 1;
    DBMS_OUTPUT.PUT_LINE('T2 inventory own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        RAISE_APPLICATION_ERROR(-20917,
            'False pass: tenant 2 sees 0 inventory rows (seed not applied?)');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: tenant 2 saw tenant 1 inventory rows!');
    END IF;

    SELECT COUNT(*) INTO v_own FROM payment;
    SELECT COUNT(*) INTO v_cross FROM payment WHERE tenant_id = 1;
    DBMS_OUTPUT.PUT_LINE('T2 payment own=' || v_own || ' cross=' || v_cross);
    IF v_own = 0 THEN
        DBMS_OUTPUT.PUT_LINE('NOTE: tenant 2 has no payment rows - leak check is vacuous for payment here');
    END IF;
    IF v_cross > 0 THEN
        RAISE_APPLICATION_ERROR(-20916, 'Cross-tenant leak: tenant 2 saw tenant 1 payment rows!');
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

-- Clean up: reset context for the shared pool
EXEC pkg_tenant.clear_tenant_context;

PROMPT === SECURITY: tenant isolation PASSED ===
EXIT SUCCESS
