-- V014: Add VPD policies to Materialized Views

-- Policy for MV_ORDER_DAILY_SUMMARY
BEGIN
    DBMS_RLS.DROP_POLICY('APP_OWNER', 'MV_ORDER_DAILY_SUMMARY', 'POL_MV_ORDER_TENANT');
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

BEGIN
    DBMS_RLS.ADD_POLICY(
        object_schema   => 'APP_OWNER',
        object_name     => 'MV_ORDER_DAILY_SUMMARY',
        policy_name     => 'POL_MV_ORDER_TENANT',
        function_schema => 'APP_OWNER',
        policy_function => 'PKG_VPD.TENANT_PREDICATE',
        statement_types => 'SELECT',
        update_check    => FALSE,
        sec_relevant_cols => 'TENANT_ID'
    );
END;
/

-- Policy for MV_PRODUCT_SALES_SUMMARY
BEGIN
    DBMS_RLS.DROP_POLICY('APP_OWNER', 'MV_PRODUCT_SALES_SUMMARY', 'POL_MV_PRODUCT_TENANT');
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

BEGIN
    DBMS_RLS.ADD_POLICY(
        object_schema   => 'APP_OWNER',
        object_name     => 'MV_PRODUCT_SALES_SUMMARY',
        policy_name     => 'POL_MV_PRODUCT_TENANT',
        function_schema => 'APP_OWNER',
        policy_function => 'PKG_VPD.TENANT_PREDICATE',
        statement_types => 'SELECT',
        update_check    => FALSE,
        sec_relevant_cols => 'TENANT_ID'
    );
END;
/
