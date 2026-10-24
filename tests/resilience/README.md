# Resilience suite (SRS §117, FT-001…)

Index:

| ID | File | What | How to run |
|---|---|---|---|
| R01 | Rake task | Delete backend pod, assert recovery via `/internal/health` | `rake qa:resilience:backend_restart` |
| R02 | `R02_db_outage.sql` + Rake task | Controlled 503 on DB outage, Unready-no-restart, recovery (FT-001) | `rake qa:resilience:db_outage` |
| R03 | `R03_pool_exhaustion.md` | Manual/limits procedure (lab cannot truly exhaust — honest scope) | Read + follow steps |
| R04 | `R04_vpd_policy_absence.sql` | Fail-loud VPD gap detection (wraps `verify_vpd_policies.sql`) | `sqlplus app_owner/... @tests/resilience/R04_vpd_policy_absence.sql` |

Lab vs procedural: R01 and R02 are orchestrated by the Rakefile and require
the `poc-oracle` kind cluster. R03 is procedural by design. Live validation
of these tasks must be recorded separately from local unit/build checks.
