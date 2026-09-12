--=====================================================================
-- GoldenGate Replicat setup (run on TARGET GGSCI host, SRS §2, §96)
--
-- Registers the Replicat group against the target remote trail and the
-- checkpoint table. Replays source OLTP changes into the target PDB
-- (Reporting / DR site).
--=====================================================================

-- 1) Create the checkpoint table on the target
ADD CHECKPOINT TABLE gg_owner.ggs_checkpoint,
    CREATE,
    TABLESPACE users;

-- 2) Register the Replicat group against the remote trail
ADD REPLICAT REP_OLTP, EXTTRAIL /u01/ggate/dirdat/lt,
    CHECKPOINT TABLE gg_owner.ggs_checkpoint;

-- 3) Assign the parameter group
ADD REPLICAT REP_OLTP, PARAMGROUP rep_oltp;

-- 4) Start applying captured changes
START REPLICAT REP_OLTP;

INFO REPLICAT *;
