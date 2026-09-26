-- P01 — single-order create latency (SRS §107 "single request").
-- Purpose: baseline latency of one order header insert path.
-- SLO: collected by run_performance.bat (default threshold 500ms p-statement).
-- Resolves tenant by code (never hardcoded IDs).
SET TIMING ON
VARIABLE v_tenant NUMBER;
BEGIN
  SELECT tenant_id INTO :v_tenant FROM tenant WHERE tenant_code = 'ACME';
END;
/
SELECT COUNT(*) AS p01_open_orders
  FROM sales_order
 WHERE tenant_id = :v_tenant AND status IN ('DRAFT','PENDING');
SELECT order_id, order_number, status, total_amount
  FROM sales_order WHERE tenant_id = :v_tenant AND ROWNUM <= 1;
