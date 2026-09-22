--=====================================================================
-- GoldenGate Replicat setup (run on TARGET GGSCI host, SRS §2, §96)
--
-- Registers the Replicat group against the target remote trail and the
-- checkpoint table. Replays source OLTP changes into the target PDB
-- (Reporting / DR site).
--
-- NOTE: ILLUSTRATIVE GGSCI COMMANDS - not executed in the local lab
-- (no GoldenGate installation); see goldengate/docs/README.md.
-- Commands are typed at the GGSCI prompt (not run in SQL*Plus).
--=====================================================================

-- 1) Create the checkpoint table on the target
ADD CHECKPOINT TABLE gg_owner.ggs_checkpoint,
    CREATE,
    TABLESPACE users;

-- 2) Register the Replicat group against the remote trail
ADD REPLICAT REP_OLTP, EXTTRAIL /u01/ggate/dirdat/lt,
    CHECKPOINT TABLE gg_owner.ggs_checkpoint;

-- 3) Start applying captured changes. Parameter file:
--    dirprm/rep_oltp.prm
START REPLICAT REP_OLTP;

INFO REPLICAT *;
