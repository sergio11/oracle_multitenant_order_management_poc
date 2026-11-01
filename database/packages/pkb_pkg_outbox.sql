--=====================================================================
-- PKG_OUTBOX - Package body
--=====================================================================
CREATE OR REPLACE PACKAGE BODY pkg_outbox AS

    --=================================================================
    -- relay_once
    --
    -- Claims a bounded batch of NEW events and marks them PUBLISHED.
    --
    -- No COMMIT here: the caller owns the transaction. Called from the
    -- backend scheduler, the whole batch commits or rolls back together,
    -- so a crash leaves the events NEW and the next run retries them --
    -- the at-least-once guarantee the outbox exists for.
    --
    -- Two things this deliberately does NOT pretend:
    --   1. The PUBLISH step is a no-op sink in this POC (there is no
    --      broker). The state transitions are real rows, so the pattern
    --      is provable end to end; replacing the sink with a real
    --      publisher does not change the contract.
    --   2. Tenure of the PROCESSING state is the transaction, not a
    --      claim: it is never durable, because nothing commits between
    --      it and PUBLISHED. SKIP LOCKED is what makes concurrent relays
    --      disjoint (two relays cannot see the same NEW row at once).
    --      A durable claim would need a separate commit plus recovery of
    --      stale PROCESSING rows - out of scope here, and the reason no
    --      component is allowed to assume PROCESSING is observable.
    --=================================================================
    FUNCTION relay_once(p_limit IN NUMBER DEFAULT 100) RETURN NUMBER IS
        v_count PLS_INTEGER := 0;
    BEGIN
        IF p_limit IS NULL OR p_limit <= 0 THEN
            RAISE_APPLICATION_ERROR(-20039, 'Outbox relay batch size must be positive.');
        END IF;

        -- Batch bound is ROWNUM, not FETCH FIRST. Oracle refuses
        -- FETCH FIRST ... FOR UPDATE (ORA-02014: row limiting wraps the
        -- query in a view and a view with a row-window cannot be locked
        -- through). ROWNUM is a plain filter, so the rows stay lockable.
        --
        -- Consequence, stated plainly: the batch is "up to p_limit NEW
        -- rows", not "the p_limit oldest". FIFO would need ORDER BY in
        -- the locking query - legal here, but a PL/SQL cursor prefetches
        -- ~100 rows, so a limit of 10 would lock 90 rows it never
        -- processes and starve a concurrent relay. Correctness over
        -- ordering: the sink is a no-op in this POC, so publish order
        -- carries no meaning yet.
        FOR r IN (
            SELECT event_id
              FROM outbox_event
             WHERE status = 'NEW'
               AND ROWNUM <= p_limit
               FOR UPDATE SKIP LOCKED
        ) LOOP
            UPDATE outbox_event
               SET status = 'PROCESSING'
             WHERE event_id = r.event_id;

            -- Publish sink (no broker in this POC).
            UPDATE outbox_event
               SET status       = 'PUBLISHED',
                   processed_at = SYSTIMESTAMP
             WHERE event_id = r.event_id;

            v_count := v_count + 1;
        END LOOP;

        RETURN v_count;
    END relay_once;

END pkg_outbox;
/

-- The relay is invoked by the backend scheduler as APP_RUNTIME.
GRANT EXECUTE ON pkg_outbox TO app_runtime;
