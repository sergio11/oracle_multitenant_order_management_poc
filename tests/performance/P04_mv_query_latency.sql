-- P04 — reporting MV query latency (SRS §107 "reporting workload").
-- Purpose: MV read latency for both reporting views.
SET TIMING ON
SELECT * FROM MV_ORDER_DAILY_SUMMARY
 WHERE tenant_id = (SELECT tenant_id FROM tenant WHERE tenant_code = 'ACME')
   AND ROWNUM <= 30;
SELECT * FROM MV_PRODUCT_SALES_SUMMARY
 WHERE tenant_id = (SELECT tenant_id FROM tenant WHERE tenant_code = 'ACME')
   AND ROWNUM <= 30;
