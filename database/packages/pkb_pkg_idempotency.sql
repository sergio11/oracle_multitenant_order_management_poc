--=====================================================================
-- PKG_IDEMPOTENCY - Package body
--=====================================================================
CREATE OR REPLACE PACKAGE BODY pkg_idempotency AS

    FUNCTION purge_expired(p_retention_days IN NUMBER DEFAULT 30) RETURN NUMBER IS
        v_deleted PLS_INTEGER := 0;
    BEGIN
        IF p_retention_days IS NULL OR p_retention_days < 1 THEN
            RAISE_APPLICATION_ERROR(-20040, 'Idempotency retention must be at least 1 day.');
        END IF;

        -- Stale in-flight rows: a PENDING row is written and completed in
        -- the same transaction, so one that is still PENDING after a day
        -- belongs to a crashed/aborted request. Keeping it would answer
        -- every future retry with 409 IDEMPOTENCY_IN_PROGRESS forever.
        DELETE FROM idempotency_key
         WHERE status = 'PENDING'
           AND created_at < SYSTIMESTAMP - INTERVAL '1' DAY;
        v_deleted := v_deleted + SQL%ROWCOUNT;

        -- Finished rows past the replay window.
        DELETE FROM idempotency_key
         WHERE status = 'COMPLETED'
           AND created_at < SYSTIMESTAMP - NUMTODSINTERVAL(p_retention_days, 'DAY');
        v_deleted := v_deleted + SQL%ROWCOUNT;

        RETURN v_deleted;
    END purge_expired;

END pkg_idempotency;
/

-- Invoked by the backend maintenance scheduler as APP_RUNTIME.
GRANT EXECUTE ON pkg_idempotency TO app_runtime;
