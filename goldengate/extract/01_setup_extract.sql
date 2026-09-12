--=====================================================================
-- GoldenGate Extract setup (run on SOURCE GGSCI host, SRS §2, §96)
--
-- Registers the Extract group, the local/remote trail, and the
-- checkpoint table used to guarantee at-least-once delivery across
-- restarts in the enterprise DR/replication architecture.
--=====================================================================

-- 1) Create the checkpoint (consistent) table in the source CDB/PDB
ADD CHECKPOINT TABLE gg_owner.ggs_checkpoint,
    CREATE,
    TABLESPACE users;

-- 2) Register the Extract group against the source container
ADD EXTRACT ext_oltp, TRANLOG, TRANSCoordINATION;

-- 3) Attach a local trail file (write path on the source)
ADD EXTTRAIL /u01/ggate/dirdat/lt, EXTRACT EXT_OLTP,
    RMTHOST = NULL,
    RMTTRAIL = NULL,
    SEQNO = 999999;

-- 4) Assign the parameter group
ADD EXTRACT EXT_OLTP, PARAMGROUP ext_oltp;

-- 5) Begin extraction from current point (initial load handled separately)
BEGIN EXT_OLTP;

INFO EXTRACT *;
