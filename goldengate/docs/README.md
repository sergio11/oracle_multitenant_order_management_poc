# GoldenGate Replication

Replication layer for the Enterprise Order Platform DR/Reporting architecture
(SRS §2, §96). The source is the OLTP PDB (`PDB_OLTP`) on the primary site;
the target is the Reporting/DR PDB on the standby.

## Components (this directory)

| File | Purpose |
|------|---------|
| `parameter-files/ggsci_extract_params.txt` | Extract parameter group (captures APP_OWNER tables) |
| `parameter-files/ggsci_replicat_params.txt` | Replicat parameter group (replays into target APP_OWNER) |
| `extract/01_setup_extract.sql` | GGSCI commands to register the Extract group + trail + checkpoint table |
| `replicat/01_setup_replicat.sql` | GGSCI commands to register the Replicat group on the target |

## Flow

1. **Source host:** run `extract/01_setup_extract.sql` inside `ggsci` to register
   the `EXT_OLTP` Extract group against `TRANLOG` and the local trail
   `/u01/ggate/dirdat/lt`.
2. **Extract** captures committed DML on all schema tables listed in
   `ggsci_extract_params.txt` (tenanted OLTP tables).
3. **Trail transfer:** the trail file is shipped to the target host (configured
   in the GGSCI `RMTHOST`/`RMTTRAIL` of the source extract).
4. **Target host:** run `replicat/01_setup_replicat.sql` inside `ggsci` to
   register the `REP_OLTP` Replicat group against the remote trail and the
   checkpoint table.
5. **Replicat** replays changes with `MAP APP_OWNER.* TARGET APP_OWNER.*` and
   `COMMIT_SERIALIZATION PARTIAL`.

## Prerequisites

- GoldenGate for Oracle 19c+ installed on both source and target.
- The `gg_owner` Oracle user must exist on both CDB/PDB with the
  `GOLDENGATE_GG_ROLE` role (grants `SELECT_CATALOG_ROLE`, `FLASHBACK` on
  captured tables, and `INSERT`/`UPDATE`/`DELETE` on replicated tables).
- Network connectivity between source and target GGSCI hosts (port 7807/7808
  plus the trail TCP port).
- Password store (`ggsci_cred_store`) configured instead of plaintext
  `PASSWORD` (see `ggsci_*_params.txt`).

## Notes

- Initial load (historical data) is performed once via `ADD LOAD DATA` before
  starting the Extract; the trail only captures changes thereafter.
- `HANDLECOLLISIONS` is set on Replicat for the DR site (last-write-wins).
  For pure Reporting this can be removed once replication is in sync.
