# Runbook — RMAN Backup & Restore

> Status: SCRIPTS PROVIDED, NOT executed live in this lab (no RMAN run against
> the container Oracle was performed). Targets CDB/PDB_OLTP layout.

## Full backup (database/backup/rman_full_backup.rcv)

1. `rman target / @database/backup/rman_full_backup.rcv`
2. Verifies: `sqlplus / as sysdba @database/backup/backup_verify.sql`
   (latest `V$RMAN_BACKUP_JOB_DETAILS` success).

## Restore (database/backup/rman_restore.bat)

1. Stop backend (avoid writes). 2. `tests? no — database\backup\rman_restore.bat`
   (pre-checks: RMAN reachable, target CDB/PDB names confirmed, backup pieces
   visible). 3. RMAN: startup nomount → restore controlfile → restore database
   → recover → open resetlogs (PDB_OLTP opened explicitly). 4. Re-run
   `backup_verify.sql` + `verify_vpd_policies.sql` (policies are data-dictionary
   state, restored with the DB) + smoke.
