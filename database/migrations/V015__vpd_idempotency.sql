--=====================================================================
-- V015 - VPD policy for IDEMPOTENCY_KEY
--
-- The IDEMPOTENCY_KEY policy was originally declared in V011, but that
-- migration runs BEFORE V012 creates the table (ORA-00942). The policy
-- is therefore created here, after the table exists.
--
-- Same parameters as the other tenant-aware policies: fail-closed
-- predicate via PKG_VPD.TENANT_PREDICATE, update_check => TRUE so
-- INSERT/UPDATE are tenant-checked too (SRS 14, 93).
--=====================================================================

-- Drop if present (ignore missing on first run / re-run)
BEGIN
    DBMS_RLS.DROP_POLICY('APP_OWNER','IDEMPOTENCY_KEY','POL_IDEMP_TENANT');
EXCEPTION
    WHEN OTHERS THEN
        NULL;
END;
/

BEGIN
    DBMS_RLS.ADD_POLICY('APP_OWNER','IDEMPOTENCY_KEY','POL_IDEMP_TENANT','APP_OWNER','PKG_VPD.TENANT_PREDICATE','SELECT,INSERT,UPDATE,DELETE',TRUE);
END;
/
