--=====================================================================
-- Concurrency test: stock setup
--
-- Resets one product/warehouse to an exact quantity before launching
-- the parallel workers. Run as APP_OWNER (closure fix: APP_RUNTIME no
-- longer holds EXECUTE on pkg_inventory, so the set_test_stock helper
-- is only reachable by the schema owner - V023).
--
-- Args:
--   &1 p_tenant_id
--   &2 p_product_id
--   &3 p_warehouse_id
--   &4 p_qty
--=====================================================================

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE

-- Run as APP_OWNER: resolve unqualified names against the owner schema.
ALTER SESSION SET CURRENT_SCHEMA = APP_OWNER;

DEFINE p_tenant_id    = &1
DEFINE p_product_id   = &2
DEFINE p_warehouse_id = &3
DEFINE p_qty          = &4

-- NOTE: direct DBMS_SESSION.SET_CONTEXT is forbidden outside the
-- designated package (ORA-01031) - use the pkg_tenant API.
EXEC pkg_tenant.set_tenant_context(&p_tenant_id);

BEGIN
    pkg_inventory.set_test_stock(&p_tenant_id, &p_product_id, &p_warehouse_id, &p_qty);
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Stock set: product &p_product_id -> qty &p_qty');
END;
/

EXIT SUCCESS
