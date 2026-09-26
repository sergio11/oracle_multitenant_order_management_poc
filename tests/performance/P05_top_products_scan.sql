-- P05 — top-products large-limit scan (limit=100, max allowed).
-- Purpose: worst-case reporting read (SRS §107 "large tenant").
SET TIMING ON
SELECT * FROM (
  SELECT product_id, quantity_sold, revenue, order_count
    FROM MV_PRODUCT_SALES_SUMMARY
   WHERE tenant_id = (SELECT tenant_id FROM tenant WHERE tenant_code = 'ACME')
   ORDER BY revenue DESC
) WHERE ROWNUM <= 100;
