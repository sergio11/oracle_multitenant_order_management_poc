# Runbook — Flashback (Logical Recovery)

> Status: PROCEDURAL, NOT executed live (lab flashback not run; commands are
> standard Oracle, adapt to CDB/PDB scope).

Prereqs: flashback enabled (`FLASHBACK ON` for CDB/PDB as applicable),
sufficient undo / flashback logs, supplemental logging if needed.

```sql
-- Investigate (as APP_OWNER or privileged user):
SELECT * FROM sales_order AS OF TIMESTAMP (SYSTIMESTAMP - INTERVAL '15' MINUTE)
 WHERE tenant_id = :t AND order_id = :o;
-- Recover a table (requires row movement):
ALTER TABLE sales_order ENABLE ROW MOVEMENT;
FLASHBACK TABLE sales_order TO TIMESTAMP (SYSTIMESTAMP - INTERVAL '15' MINUTE);
-- Or database-level (downtime, SYS):
SHUTDOWN IMMEDIATE; STARTUP MOUNT;
FLASHBACK DATABASE TO TIMESTAMP (SYSTIMESTAMP - INTERVAL '30' MINUTE);
ALTER DATABASE OPEN RESETLOGS;
```

Notes: Flashback ≠ backup (does not replace RMAN). VPD still applies —
recover under the right tenant context.
