-- P02 — confirm throughput path (SRS §107 "concurrent requests").
-- Purpose: exercise the confirm read-set (order+lines+inventory join).
-- SLO: collected by runner; asserts no full-table scans expected via indexes.
SET TIMING ON
SELECT o.order_id, o.status, l.product_id, l.quantity,
       i.available_quantity, i.reserved_quantity
  FROM sales_order o
  JOIN sales_order_line l ON l.order_id = o.order_id AND l.tenant_id = o.tenant_id
  JOIN inventory i ON i.product_id = l.product_id AND i.tenant_id = o.tenant_id
 WHERE o.tenant_id = (SELECT tenant_id FROM tenant WHERE tenant_code = 'ACME')
   AND o.status = 'PENDING' AND ROWNUM <= 50;
