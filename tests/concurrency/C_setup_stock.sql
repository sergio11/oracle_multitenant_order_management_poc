--=====================================================================
-- Concurrency test: stock setup
--
-- Resets one product/warehouse to an exact quantity before launching
-- the parallel workers. Run as APP_RUNTIME (needs VPD context).
--
-- Args:
--   &1 p_tenant_id
--   &2 p_product_id
--   &3 p_warehouse_id
--   &4 p_qty
--=====================================================================

SET SERVEROUTPUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE

DEFINE p_tenant_id    = &1
DEFINE p_product_id   = &2
DEFINE p_warehouse_id = &3
DEFINE p_qty          = &4

BEGIN
    DBMS_SESSION.SET_CONTEXT('APP_CTX', 'TENANT_ID', '&p_tenant_id');
    pkg_inventory.set_test_stock(&p_tenant_id, &p_product_id, &p_warehouse_id, &p_qty);
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Stock set: product &p_product_id -> qty &p_qty');
END;
/

EXIT SUCCESS
