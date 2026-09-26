# Runbook — GoldenGate (Extract/Replicat)

> Status: ILLUSTRATIVE, NOT deployed locally (no GG installation in lab).
> Files: `goldengate/extract/01_setup_extract.ggsci`,
> `goldengate/replicat/01_setup_replicat.ggsci`,
> `goldengate/parameter-files/ggsci_{extract,replicat}_params.txt`.

1. Credential store (both hosts): `ADD CREDENTIALSTORE` (wallet) then
   `ALTER CREDENTIALSTORE ADD USER ogg_admin@<PDB> PASSWORD <secret>
   ALIAS ogg_admin` (params reference `USERIDALIAS ogg_admin`).
2. Checkpoint table: `ADD CHECKPOINTTABLE gg_owner.ggs_checkpoint` (+ replicat
   `01_setup_replicat.ggsci` equivalent).
3. Extract: `ADD EXTRACT ext_oltp, TRANLOG, BEGIN NOW` +
   `ADD EXTTRAIL /u01/ggate/dirdat/lt, EXTRACT ext_oltp`
   (`RMTHOST <replicat-host>, MGRPORT 7809`, `RMTTRAIL ./dirdat/rt`).
4. Replicat: `ADD REPLICAT rep_oltp, EXTTRAIL ./dirdat/rt, CHECKPOINTTABLE ...`.
5. Start: `START EXTRACT ext_oltp` / `START REPLICAT rep_oltp`; monitor
   `INFO EXTRACT *`, `INFO REPLICAT *`, `LAG`, `STATS`, error log.
6. Tenant note (SRS §67): `TENANT_ID` replicates as data; VPD does not filter GG.
