# Runbook — Data Guard Switchover (Planned)

> Status: PROCEDURAL, NOT executed locally (single-node lab; no standby
> exists). Enterprise-target procedure only.

1. Verify transport: primary `V$ARCHIVE_DEST_STATUS` (VALID, zero gap),
   standby `MRP` applying, `V$DATAGUARD_STATS` lag within RPO (≤1 min target).
2. Switchover (DGMGRL): `SWITCHOVER TO <standby>;` — roles exchange, no data loss.
3. Verify: new primary open RW, services (PDB_OLTP) started, backend UCP URL
   repointed/failed over; run `qa:smoke`.
4. Reinstate old primary as standby: `REINSTATE DATABASE <old>;` + start MRP.
5. Failover (unplanned, separate path) is loss-assessed first and NOT covered
   by this switchover procedure.

Rollback: if validation fails pre-switchover, abort (no role change has
occurred yet).
