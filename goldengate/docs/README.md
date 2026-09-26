# GoldenGate Replication

> **Note:** The GoldenGate configuration in this directory is **illustrative
> only and is NOT deployed in the local lab** (no GoldenGate installation);
> see the header comments in the setup scripts.

Replication layer for the Enterprise Order Platform DR/Reporting architecture
(SRS §2, §96). The source is the OLTP PDB (`PDB_OLTP`) on the primary site;
the target is the Reporting/DR PDB on the standby.

## Components (this directory)

| File | Purpose |
|------|---------|
| `parameter-files/ggsci_extract_params.txt` | Extract parameter group (captures APP_OWNER tables; `USERIDALIAS ogg_admin`, `RMTHOST <ogg-replicat-host>, MGRPORT 7809` + `RMTTRAIL ./dirdat/rt`) |
| `parameter-files/ggsci_replicat_params.txt` | Replicat parameter group (replays into target APP_OWNER; `USERIDALIAS ogg_admin`) |
| `extract/01_setup_extract.ggsci` | GGSCI commands to register the Extract group + trail + checkpoint table |
| `replicat/01_setup_replicat.ggsci` | GGSCI commands to register the Replicat group on the target |

## Flow

1. **Source host:** run `extract/01_setup_extract.ggsci` inside `ggsci` to register
   the `EXT_OLTP` Extract group against `TRANLOG` and the local trail
   `/u01/ggate/dirdat/lt`.
2. **Extract** captures committed DML on all schema tables listed in
   `ggsci_extract_params.txt` (tenanted OLTP tables).
3. **Trail transfer:** the trail file is shipped to the target host via
   `RMTHOST <ogg-replicat-host>, MGRPORT 7809` and `RMTTRAIL ./dirdat/rt`
   in the source extract parameter file.
4. **Target host:** run `replicat/01_setup_replicat.ggsci` inside `ggsci` to
   register the `REP_OLTP` Replicat group against the remote trail and the
   checkpoint table.
5. **Replicat** replays changes with `MAP APP_OWNER.* TARGET APP_OWNER.*` and
   `COMMIT_SERIALIZATION PARTIAL`.

## Prerequisites

- GoldenGate for Oracle 19c+ installed on both source and target.
- The `ogg_admin` Oracle user must exist on both CDB/PDB with the
  `GOLDENGATE_GG_ROLE` role (grants `SELECT_CATALOG_ROLE`, `FLASHBACK` on
  captured tables, and `INSERT`/`UPDATE`/`DELETE` on replicated tables).
  The checkpoint table `gg_owner.ggs_checkpoint` (see `*.ggsci` setups)
  must be creatable by this user.
- Network connectivity between source and target GGSCI hosts (port 7807/7808
  plus the trail TCP port; extract uses `MGRPORT 7809` on
  `<ogg-replicat-host>`).
- Credential store configured instead of plaintext `PASSWORD`
  (parameter files use `USERIDALIAS ogg_admin`). Register once per host
  in `ggsci`:
  ```
  ADD CREDENTIALSTORE
  ALTER CREDENTIALSTORE ADD USER ogg_admin@<pdb-connect-string> PASSWORD <password> ALIAS ogg_admin
  ```
  Use the source PDB connect string on the extract host and the target PDB
  connect string on the replicat host.

## Notes

- Initial load (historical data) is performed once via `ADD LOAD DATA` before
  starting the Extract; the trail only captures changes thereafter.
- `HANDLECOLLISIONS` is set on Replicat for the DR site (last-write-wins).
  For pure Reporting this can be removed once replication is in sync.
