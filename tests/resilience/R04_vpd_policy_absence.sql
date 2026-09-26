-- R04_vpd_policy_absence.sql — fail loudly if any VPD policy is missing.
-- Wraps database/security/verify_vpd_policies.sql (16 policies expected).
-- Usage (as APP_OWNER): sqlplus app_owner/<pwd>@//localhost:1521/pdb_oltp
--   @tests/resilience/R04_vpd_policy_absence.sql
WHENEVER SQLERROR EXIT FAILURE
PROMPT === R04: VPD policy absence check (fails loudly on any gap) ===
@../../database/security/verify_vpd_policies.sql
PROMPT === R04 PASSED: all 16 VPD policies present ===
