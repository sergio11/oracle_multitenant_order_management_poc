# Resilience suite (SRS §117, FT-001…)

Index:

| ID | File | What | How to run |
|---|---|---|---|
| R01 | `R01_backend_kill.bat` | Restart backend container, assert recovery via `/internal/health` | `tests\resilience\R01_backend_kill.bat` (repo root) |
| R02 | `R02_db_outage.sql` + `R02_db_outage.bat` | Controlled 503 on DB outage, Unready-no-restart, recovery (FT-001) | `tests\resilience\R02_db_outage.bat` |
| R03 | `R03_pool_exhaustion.md` | Manual/limits procedure (lab cannot truly exhaust — honest scope) | Read + follow steps |
| R04 | `R04_vpd_policy_absence.sql` | Fail-loud VPD gap detection (wraps `verify_vpd_policies.sql`) | `sqlplus app_owner/... @tests/resilience/R04_vpd_policy_absence.sql` |

Lab vs procedural: the FT-001 *scenario* (DB outage → controlled 503,
Unready-no-restart, recovery) WAS executed manually on kind via the
`kubectl scale statefulset oracle` procedure documented in the root
README QA section — the R01/R02 `.bat` wrappers themselves are UNEXECUTED
in this pass (no scripted run claimed); R03 is procedural by design.
