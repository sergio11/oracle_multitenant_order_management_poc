-- P06 — concurrent mixed read/write probe (SRS §107 "inventory contention").
-- Purpose: read-side of the contended inventory row + reservation state.
-- Run N copies in parallel; the runner launches them sequentially but notes
-- the parallel procedure in its header.
SET TIMING ON
SELECT i.product_id, i.warehouse_id, i.available_quantity, i.reserved_quantity, i.version
  FROM inventory i
 WHERE i.tenant_id = (SELECT tenant_id FROM tenant WHERE tenant_code = 'ACME')
   AND i.product_id = (SELECT MIN(product_id) FROM product
                        WHERE tenant_id = i.tenant_id)
   AND ROWNUM <= 10;
SELECT COUNT(*) AS p06_outbox_new FROM outbox_event
 WHERE tenant_id = (SELECT tenant_id FROM tenant WHERE tenant_code = 'ACME')
   AND status = 'NEW';
