--=====================================================================
-- IT_outbox_relay.sql
--
-- Proves the outbox relay half of the pattern, not just the writer:
--   1. a real business operation (confirm_order) writes ORDER_CONFIRMED
--      as NEW in the same transaction;
--   2. pkg_outbox.relay_once claims it with SKIP LOCKED and drives it
--      NEW -> PROCESSING -> PUBLISHED;
--   3. the row is visible as PUBLISHED afterwards.
--
-- Runs as APP_RUNTIME with the per-tenant VPD context set, i.e. through
-- exactly the same path the backend uses.
--
-- The confirm/cancel pair leaves no reserved stock behind (cancel
-- releases the reservation and writes ORDER_CANCELLED, which stays NEW
-- for the next relay run - by design).
--=====================================================================
SET SERVEROUTPUT ON SIZE UNLIMITED
SET FEEDBACK OFF
WHENEVER SQLERROR EXIT SQL.SQLCODE

-- Run as APP_RUNTIME: resolve unqualified names (tables, packages)
-- against the owner schema. There are no synonyms by design (least
-- privilege), so without this every reference fails as PLS-00201 /
-- ORA-00942 even though the grants exist.
ALTER SESSION SET CURRENT_SCHEMA = APP_OWNER;

DECLARE
    v_tenant_id    NUMBER;
    v_customer_id  NUMBER;
    v_product_id   NUMBER;
    v_warehouse_id NUMBER;
    v_order_id     NUMBER;
    v_event_id     NUMBER;
    v_status       VARCHAR2(20);
    v_relayed      NUMBER;
BEGIN
    SELECT tenant_id INTO v_tenant_id FROM tenant WHERE tenant_code = 'ACME';
    pkg_tenant.set_tenant_context(v_tenant_id);

    SELECT MIN(customer_id) INTO v_customer_id
      FROM customer WHERE tenant_id = v_tenant_id;
    SELECT MIN(product_id) INTO v_product_id
      FROM product WHERE tenant_id = v_tenant_id;
    SELECT MIN(warehouse_id) INTO v_warehouse_id
      FROM warehouse WHERE tenant_id = v_tenant_id AND status = 'ACTIVE';

    v_order_id := pkg_order_management.create_order(v_tenant_id, v_customer_id, 'USD');
    DBMS_OUTPUT.PUT_LINE('Created order_id=' || v_order_id);
    pkg_order_management.add_order_line(v_tenant_id, v_order_id, v_product_id, 1, v_warehouse_id);
    pkg_order_management.confirm_order(v_tenant_id, v_order_id, 'IT_OUTBOX');

    SELECT MAX(event_id) INTO v_event_id
      FROM outbox_event
     WHERE tenant_id = v_tenant_id
       AND aggregate_id = TO_CHAR(v_order_id)
       AND event_type = 'ORDER_CONFIRMED';

    IF v_event_id IS NULL THEN
        RAISE_APPLICATION_ERROR(-20900, 'FAIL: confirm_order wrote no ORDER_CONFIRMED event');
    END IF;

    SELECT status INTO v_status FROM outbox_event WHERE event_id = v_event_id;
    IF v_status <> 'NEW' THEN
        RAISE_APPLICATION_ERROR(-20901,
            'FAIL: expected event NEW before relay, found ' || v_status);
    END IF;
    DBMS_OUTPUT.PUT_LINE('[PASS] writer: ORDER_CONFIRMED event ' || v_event_id || ' is NEW');

    v_relayed := pkg_outbox.relay_once(100);
    IF v_relayed < 1 THEN
        RAISE_APPLICATION_ERROR(-20902, 'FAIL: relay published no events');
    END IF;
    DBMS_OUTPUT.PUT_LINE('[PASS] relay: published ' || v_relayed || ' event(s) in one batch');

    SELECT status INTO v_status FROM outbox_event WHERE event_id = v_event_id;
    IF v_status <> 'PUBLISHED' THEN
        RAISE_APPLICATION_ERROR(-20903,
            'FAIL: expected event PUBLISHED after relay, found ' || v_status);
    END IF;
    DBMS_OUTPUT.PUT_LINE('[PASS] reader: event ' || v_event_id || ' is PUBLISHED');

    -- Release the reservation and leave a deterministic next-run event.
    pkg_order_management.cancel_order(
        p_tenant_id => v_tenant_id,
        p_order_id  => v_order_id,
        p_reason    => 'IT_OUTBOX cleanup',
        p_actor     => 'IT_OUTBOX');
    DBMS_OUTPUT.PUT_LINE('[PASS] cleanup: order ' || v_order_id || ' cancelled, stock released');

    DBMS_OUTPUT.PUT_LINE('=== IT: outbox relay PASSED ===');
END;
/

EXIT SUCCESS
