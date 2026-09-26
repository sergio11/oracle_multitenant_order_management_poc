# R03 — Pool Exhaustion (Procedural / Limits)

Scope honesty: the lab cannot truly exhaust the UCP pool — pool sizing is
deliberately aggressive-for-lab (`max-pool-size: 20`, `connection-wait-timeout:
30s` local / 5s prod) and FT-002 is excluded by design (README QA evidence).

Manual procedure (when attempted on adequate hardware):

1. Lower `oracle.ucp.max-pool-size` (e.g. 2) and `connection-wait-timeout`
   (e.g. 5s) via env/overrides; restart backend.
2. Generate blocking load: open N parallel `sqlplus` sessions holding locks
   (`SELECT ... FOR UPDATE` on distinct inventory rows, uncommitted) plus
   parallel API confirm calls exceeding pool size.
3. Expected: `getConnection()` waits, then fails with a controlled 503/
   DATABASE_ERROR (no thread hang, no unbounded queue); pod stays Ready
   (liveness is app-only) unless `db` health also degrades.
4. Recover: commit/rollback blockers, restore pool settings, re-run smoke.

Record: pool metrics (`/internal/prometheus`, UCP MBean), wait-timeout
occurrences, and that no restart was required.
