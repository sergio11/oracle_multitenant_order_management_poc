--=====================================================================
-- V025 - VPD policy repair + final-state assertion (closure fix S2/S5)
--
-- V011 / V014 / V015 create the 16 tenant-isolation policies, but their
-- ADD blocks have no compensation: a partial failure leaves tables (or
-- the reporting MVs) without a policy while later migrations keep
-- running - a silent fail-open. This migration repairs that state:
-- every expected policy that is missing, disabled, or carries the
-- wrong attributes (predicate function, statement coverage) is
-- dropped and re-added with the canonical definition, then the full
-- expected set is asserted (loud failure otherwise).
--
-- Expected set (mirrors database/security/verify_vpd_policies.sql):
--   13 base tables + order history/payment/outbox/audit (V011)
--    1 idempotency key                                 (V015)
--    2 materialized views, SELECT-only                 (V014)
-- Runs as APP_OWNER (same as V011/V014/V015).
--=====================================================================
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

    v_object    VARCHAR2(128);
    v_policy    VARCHAR2(128);
    v_count     NUMBER;
    v_repaired  NUMBER := 0;

    -- A policy counts as healthy only if it is enabled, points at
    -- PKG_VPD.TENANT_PREDICATE as APP_OWNER, and covers the right
    -- statements (full DML on tables; SELECT-only on MVs).
    FUNCTION healthy(p_object VARCHAR2, p_policy VARCHAR2) RETURN BOOLEAN IS
        v_ok NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_ok
          FROM all_policies
         WHERE object_owner = 'APP_OWNER'
           AND object_name  = p_object
           AND policy_name  = p_policy
           AND enable       = 'YES'
           AND package      = 'PKG_VPD'
           AND function     = 'TENANT_PREDICATE'
           AND pf_owner     = 'APP_OWNER'
           AND sel          = 'YES'
           AND ins          = CASE WHEN p_object LIKE 'MV\_%' ESCAPE '\' THEN 'NO' ELSE 'YES' END
           AND upd          = CASE WHEN p_object LIKE 'MV\_%' ESCAPE '\' THEN 'NO' ELSE 'YES' END
           AND del          = CASE WHEN p_object LIKE 'MV\_%' ESCAPE '\' THEN 'NO' ELSE 'YES' END;
        RETURN v_ok > 0;
    END;
BEGIN
    FOR i IN 1 .. v_expected.COUNT LOOP
        v_object := SUBSTR(v_expected(i), 1,              INSTR(v_expected(i), ':') - 1);
        v_policy := SUBSTR(v_expected(i), INSTR(v_expected(i), ':') + 1);

        IF NOT healthy(v_object, v_policy) THEN
            -- Remove any broken/disabled remnant first (tolerant: the
            -- policy may not exist at all - that is the failure we are
            -- repairing).
            BEGIN
                DBMS_RLS.DROP_POLICY('APP_OWNER', v_object, v_policy);
            EXCEPTION
                WHEN OTHERS THEN NULL;
            END;
            -- Re-add with the canonical definition (MV policies SELECT-only).
            IF v_object LIKE 'MV\_%' ESCAPE '\' THEN
                DBMS_RLS.ADD_POLICY(
                    object_schema   => 'APP_OWNER',
                    object_name     => v_object,
                    policy_name     => v_policy,
                    function_schema => 'APP_OWNER',
                    policy_function => 'PKG_VPD.TENANT_PREDICATE',
                    statement_types => 'SELECT',
                    update_check    => FALSE);
            ELSE
                DBMS_RLS.ADD_POLICY('APP_OWNER', v_object, v_policy,
                    'APP_OWNER', 'PKG_VPD.TENANT_PREDICATE',
                    'SELECT,INSERT,UPDATE,DELETE', TRUE);
            END IF;
            v_repaired := v_repaired + 1;
        END IF;
    END LOOP;

    -- Final-state assertion: fail the migration loudly if the set is
    -- still incomplete after repair.
    SELECT COUNT(*)
      INTO v_count
      FROM all_policies
     WHERE object_owner = 'APP_OWNER'
       AND policy_name IN ('POL_CUSTOMER_TENANT','POL_ADDR_TENANT','POL_ACCT_TENANT',
                           'POL_CATEGORY_TENANT','POL_PRODUCT_TENANT','POL_WAREHOUSE_TENANT',
                           'POL_INVENTORY_TENANT','POL_ORDER_TENANT','POL_LINE_TENANT',
                           'POL_HISTORY_TENANT','POL_PAYMENT_TENANT','POL_OUTBOX_TENANT',
                           'POL_IDEMP_TENANT','POL_AUDIT_TENANT',
                           'POL_MV_ORDER_TENANT','POL_MV_PRODUCT_TENANT');

    IF v_count <> 16 THEN
        RAISE_APPLICATION_ERROR(-20932,
            'VPD compensation incomplete: expected 16 policies, found ' || v_count
            || ' (repaired ' || v_repaired || ')');
    END IF;
END;
/
