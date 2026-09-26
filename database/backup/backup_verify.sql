-- backup_verify.sql — assert latest RMAN backup job succeeded.
-- Usage: sqlplus / as sysdba @database/backup/backup_verify.sql
WHENEVER SQLERROR EXIT FAILURE
SET SERVEROUTPUT ON
DECLARE
  v_status VARCHAR2(30);
  v_end    DATE;
BEGIN
  SELECT status, end_time INTO v_status, v_end FROM (
    SELECT status, end_time FROM V$RMAN_BACKUP_JOB_DETAILS
     ORDER BY end_time DESC) WHERE ROWNUM = 1;
  DBMS_OUTPUT.PUT_LINE('Latest RMAN job: ' || v_status || ' at ' || v_end);
  IF v_status NOT IN ('COMPLETED') THEN
    RAISE_APPLICATION_ERROR(-20999, 'Latest RMAN backup job not COMPLETED: ' || v_status);
  END IF;
END;
/
