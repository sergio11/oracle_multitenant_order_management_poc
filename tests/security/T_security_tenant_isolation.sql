--=====================================================================
-- Security test: VPD cross-tenant isolation (SRS Â§14, Â§52, Â§93, BR-013)
--
-- Verifies TENANT ISOLATION: once a session sets a tenant context,
-- queries can NEVER see rows belonging to another tenant. This is a
-- core defense-in-depth control (VPD + application context).
--
-- Usage:
--   sqlplus app_owner/<pwd>@//localhost:1521/pdb_oltp \
--     @tests/security/T_security_tenant_isolation.sql
--=====================================================================

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

PROMPT === SECURITY: cross-tenant isolation (VPD) ===

-- Switch to tenant 1 and count its customers
EXEC pkg_tenant.set_tenant_context(1);
DECLARE
    c1 NUMBER;
    c2 NUMBER;
BEGIN
    SELECT COUNT(*) INTO c1 FROM customer;             -- tenant 1 customers
    DBMS_OUTPUT.PUT_LINE('Tenant 1 customer count = ' || c1);

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
BEGIN
    SELECT COUNT(*) INTO c1 FROM customer WHERE tenant_id = 1;
    DBMS_OUTPUT.PUT_LINE('Tenant 2 visible rows for tenant_id=1 = ' || c1);
    IF c1 > 0 THEN
        RAISE_APPLICATION_ERROR(-20911, 'Cross-tenant leak: tenant 2 saw tenant 1 customer rows!');
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

-- Clean up: reset context for the shared pool
EXEC pkg_tenant.clear_tenant_context;

PROMPT === SECURITY: tenant isolation PASSED ===
EXIT SUCCESS