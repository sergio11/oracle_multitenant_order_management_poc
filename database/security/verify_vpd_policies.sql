--=====================================================================
-- verify_vpd_policies.sql - VPD policy set assertion (H1 / L1)
--
-- Asserts that APP_OWNER has the COMPLETE expected DBMS_RLS policy
-- set after migrations (V011, V013, V014, V015) and/or
-- apply_vpd_policies.sql. This is the detection half of the H1 fix:
-- the drop/re-add windows (and the legacy WHEN OTHERS THEN NULL drops
-- in V011/V014/V015, L1 - deliberately left untouched to avoid
-- checksum churn) may swallow errors silently; this script makes any
-- missing or disabled policy FAIL LOUDLY.
--
-- Expected set: 16 policies
--   13 base tables            (V011)
--    1 idempotency key        (V015)
--    2 materialized views     (V014)
--
-- Usage (as APP_OWNER, after `flyway migrate` and packages deploy):
--   sqlplus app_owner/<pwd>@//localhost:1521/pdb_oltp \
--     @database/security/verify_vpd_policies.sql
--
-- Exit behaviour: WHENEVER SQLERROR EXIT FAILURE -> non-zero exit code
-- on any assertion failure, so the orchestrator / QA task can gate on
-- it. Prints one FAIL line per problem plus a summary.
--=====================================================================

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT FAILURE

ALTER SESSION SET CURRENT_SCHEMA = APP_OWNER;

DECLARE
    TYPE t_expected IS TABLE OF VARCHAR2(200);
    v_expected t_expected := t_expected(
        -- V011 base tables
        'CUSTOMER'            || ':' || 'POL_CUSTOMER_TENANT',
        'CUSTOMER_ADDRESS'    || ':' || 'POL_ADDR_TENANT',
        'CUSTOMER_ACCOUNT'    || ':' || 'POL_ACCT_TENANT',
        'PRODUCT_CATEGORY'    || ':' || 'POL_CATEGORY_TENANT',
        'PRODUCT'             || ':' || 'POL_PRODUCT_TENANT',
        'WAREHOUSE'           || ':' || 'POL_WAREHOUSE_TENANT',
        'INVENTORY'           || ':' || 'POL_INVENTORY_TENANT',
        'SALES_ORDER'         || ':' || 'POL_ORDER_TENANT',
        'SALES_ORDER_LINE'    || ':' || 'POL_LINE_TENANT',
        'ORDER_STATUS_HISTORY'|| ':' || 'POL_HISTORY_TENANT',
        'PAYMENT'             || ':' || 'POL_PAYMENT_TENANT',
        'OUTBOX_EVENT'        || ':' || 'POL_OUTBOX_TENANT',
        'AUDIT_EVENT'         || ':' || 'POL_AUDIT_TENANT',
        -- V015 idempotency key
        'IDEMPOTENCY_KEY'     || ':' || 'POL_IDEMP_TENANT',
        -- V014 materialized views (SELECT-only policies)
        'MV_ORDER_DAILY_SUMMARY'   || ':' || 'POL_MV_ORDER_TENANT',
        'MV_PRODUCT_SALES_SUMMARY' || ':' || 'POL_MV_PRODUCT_TENANT'
    );

    v_object      VARCHAR2(128);
    v_policy      VARCHAR2(128);
    v_count       NUMBER;
    v_attr_ok     NUMBER;
    v_missing     NUMBER := 0;
    v_bad_attr    NUMBER := 0;
    v_is_mv       BOOLEAN;
BEGIN
    DBMS_OUTPUT.PUT_LINE('=== VPD policy verification for APP_OWNER ===');

    FOR i IN 1 .. v_expected.COUNT LOOP
        v_object := SUBSTR(v_expected(i), 1,              INSTR(v_expected(i), ':') - 1);
        v_policy := SUBSTR(v_expected(i), INSTR(v_expected(i), ':') + 1);
        v_is_mv  := v_object LIKE 'MV\_%' ESCAPE '\';

        SELECT COUNT(*)
          INTO v_count
          FROM all_policies
         WHERE object_owner = 'APP_OWNER'
           AND object_name  = v_object
           AND policy_name  = v_policy;

        IF v_count = 0 THEN
            v_missing := v_missing + 1;
            DBMS_OUTPUT.PUT_LINE('FAIL missing policy: ' || v_expected(i));
        ELSE
            -- Attribute assertions: enabled, correct predicate function,
            -- correct statement coverage. A policy that exists but is
            -- disabled or covers the wrong statements is as bad as a
            -- missing one (silent fail-open). MV policies are
            -- SELECT-only; base-table policies cover all DML.
            SELECT COUNT(*)
              INTO v_attr_ok
              FROM all_policies
             WHERE object_owner = 'APP_OWNER'
               AND object_name  = v_object
               AND policy_name  = v_policy
               AND enable       = 'YES'
               AND package      = 'PKG_VPD'
               AND function     = 'TENANT_PREDICATE'
               AND pf_owner     = 'APP_OWNER'
               AND sel          = 'YES'
               AND ins          = CASE WHEN v_object LIKE 'MV\_%' ESCAPE '\' THEN 'NO' ELSE 'YES' END
               AND upd          = CASE WHEN v_object LIKE 'MV\_%' ESCAPE '\' THEN 'NO' ELSE 'YES' END
               AND del          = CASE WHEN v_object LIKE 'MV\_%' ESCAPE '\' THEN 'NO' ELSE 'YES' END;

            IF v_attr_ok = 0 THEN
                v_bad_attr := v_bad_attr + 1;
                DBMS_OUTPUT.PUT_LINE('FAIL bad policy attributes: ' || v_expected(i) ||
                                     ' (expected enable=YES fn=PKG_VPD.TENANT_PREDICATE ' ||
                                     CASE WHEN v_is_mv THEN 'sel-only)' ELSE 'sel+ins+upd+del)' END);
            ELSE
                DBMS_OUTPUT.PUT_LINE('OK   ' || v_expected(i));
            END IF;
        END IF;
    END LOOP;

    -- Informational: any policy on APP_OWNER tables NOT in the expected
    -- set (duplicates left behind by older scripts, stray policies).
    DBMS_OUTPUT.PUT_LINE('--- policies present but not expected ---');
    FOR r IN (SELECT object_name, policy_name
                FROM all_policies
               WHERE object_owner = 'APP_OWNER'
               AND object_name || ':' || policy_name NOT IN
                   (SELECT SUBSTR(column_value, 1, INSTR(column_value, ':') - 1) || ':' ||
                           SUBSTR(column_value, INSTR(column_value, ':') + 1)
                      FROM TABLE(v_expected))
               ORDER BY object_name, policy_name) LOOP
        DBMS_OUTPUT.PUT_LINE('INFO unexpected policy: ' || r.object_name || ':' || r.policy_name);
    END LOOP;

    IF v_missing > 0 OR v_bad_attr > 0 THEN
        RAISE_APPLICATION_ERROR(-20930,
            'VPD verification FAILED: ' || v_missing || ' missing, ' ||
            v_bad_attr || ' with bad attributes (expected 16 policies)');
    END IF;

    DBMS_OUTPUT.PUT_LINE('=== VPD verification PASSED: 16/16 policies OK ===');
END;
/
