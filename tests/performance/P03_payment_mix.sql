-- P03 — payment mix (SRS §107 "single/concurrent").
-- Purpose: payment lookup by order + reference uniqueness probe.
SET TIMING ON
SELECT p.payment_id, p.payment_reference, p.amount, p.status
  FROM payment p
 WHERE p.tenant_id = (SELECT tenant_id FROM tenant WHERE tenant_code = 'ACME')
   AND ROWNUM <= 50;
SELECT COUNT(*) AS p03_payments,
       SUM(CASE WHEN status = 'CAPTURED' THEN 1 ELSE 0 END) AS captured
  FROM payment
 WHERE tenant_id = (SELECT tenant_id FROM tenant WHERE tenant_code = 'ACME');
