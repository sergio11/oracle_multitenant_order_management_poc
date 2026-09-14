--=====================================================================
-- PKG_ORDER_MANAGEMENT - Package body
--
-- Implements the order lifecycle (SRS 33, 37).
-- confirm_order runs the entire flow in ONE transaction:
--   1. Validate order/customer/lines
--   2. Lock & validate inventory
--   3. Reserve inventory (pkg_inventory)
--   4. Change order status
--   5. Insert status history
--   6. Insert audit event
--   7. Insert outbox event
--   8. COMMIT (performed by caller transaction boundary)
--
-- If any step fails, the caller (Spring @Transactional) rolls back.
--=====================================================================
CREATE OR REPLACE PACKAGE BODY pkg_order_management AS

    --=================================================================
    -- Internal helper: generate a unique order number per tenant
    --=================================================================
    FUNCTION gen_order_number(p_tenant_id IN NUMBER) RETURN VARCHAR2 IS
        v_seq NUMBER;
    BEGIN
        SELECT tenant_ord_seq.NEXTVAL INTO v_seq FROM dual;
        RETURN 'SO-' || p_tenant_id || '-' || TO_CHAR(v_seq, 'FM00000000');
    END gen_order_number;

    --=================================================================
    -- Internal helper: validate tenant is ACTIVE (FR-003)
    --=================================================================
    PROCEDURE assert_tenant_active(p_tenant_id IN NUMBER) IS
        v_status VARCHAR2(20);
    BEGIN
        SELECT status INTO v_status FROM tenant WHERE tenant_id = p_tenant_id;
        IF v_status <> 'ACTIVE' THEN
            RAISE_APPLICATION_ERROR(-20020, 'Tenant is not ACTIVE (status=' || v_status || ')');
        END IF;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20004, 'Tenant not found: ' || p_tenant_id);
    END assert_tenant_active;

    --=================================================================
    -- Internal helper: validate customer belongs to the tenant (BR-002)
    --=================================================================
    PROCEDURE assert_customer_tenant(p_tenant_id IN NUMBER, p_customer_id IN NUMBER) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM customer
         WHERE customer_id = p_customer_id AND tenant_id = p_tenant_id;

        IF v_count = 0 THEN
            RAISE_APPLICATION_ERROR(-20021, 'Customer does not belong to this tenant.');
        END IF;
    END assert_customer_tenant;

    --=================================================================
    -- Internal helper: validate product belongs to the tenant (BR-003)
    --=================================================================
    PROCEDURE assert_product_tenant(p_tenant_id IN NUMBER, p_product_id IN NUMBER) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM product
         WHERE product_id = p_product_id AND tenant_id = p_tenant_id;

        IF v_count = 0 THEN
            RAISE_APPLICATION_ERROR(-20022, 'Product does not belong to this tenant.');
        END IF;
    END assert_product_tenant;

    --=================================================================
    -- Internal helper: transition status + history + audit
    --=================================================================
    PROCEDURE transition_status(
        p_tenant_id   IN NUMBER,
        p_order_id    IN NUMBER,
        p_old_status  IN VARCHAR2,
        p_new_status  IN VARCHAR2,
        p_actor       IN VARCHAR2,
        p_correlation IN VARCHAR2,
        p_reason      IN VARCHAR2 DEFAULT NULL
    ) IS
    BEGIN
        -- Update order status + optimistic version
        UPDATE sales_order
           SET status     = p_new_status,
               updated_at = SYSTIMESTAMP,
               version    = version + 1
         WHERE order_id = p_order_id
           AND tenant_id = p_tenant_id;

        -- Status history (SRS 25, BR-009)
        INSERT INTO order_status_history (
            tenant_id, order_id, old_status, new_status, changed_by, reason
        ) VALUES (
            p_tenant_id, p_order_id, p_old_status, p_new_status, p_actor, p_reason
        );

        -- Audit (SRS 27, FR-013)
        pkg_audit.write_data_change(
            p_tenant_id      => p_tenant_id,
            p_entity_type    => 'SALES_ORDER',
            p_entity_id      => TO_CHAR(p_order_id),
            p_action         => 'STATUS_' || p_new_status,
            p_actor          => p_actor,
            p_correlation_id => p_correlation,
            p_payload        => '{"from":"' || p_old_status || '","to":"' || p_new_status || '"}'
        );
    END transition_status;

    --=================================================================
    -- Internal helper: write outbox event (SRS 28, 63)
    --=================================================================
    PROCEDURE write_outbox(
        p_tenant_id       IN NUMBER,
        p_aggregate_type  IN VARCHAR2,
        p_aggregate_id    IN VARCHAR2,
        p_event_type      IN VARCHAR2,
        p_payload         IN CLOB
    ) IS
    BEGIN
        INSERT INTO outbox_event (
            tenant_id, aggregate_type, aggregate_id, event_type, payload, status
        ) VALUES (
            p_tenant_id, p_aggregate_type, p_aggregate_id, p_event_type, p_payload, 'NEW'
        );
    END write_outbox;

    --=================================================================
    -- create_order
    --=================================================================
    FUNCTION create_order(
        p_tenant_id    IN NUMBER,
        p_customer_id  IN NUMBER,
        p_currency     IN VARCHAR2
    ) RETURN NUMBER IS
        v_order_id NUMBER;
        v_order_no  VARCHAR2(50);
    BEGIN
        assert_tenant_active(p_tenant_id);
        assert_customer_tenant(p_tenant_id, p_customer_id);

        v_order_no := gen_order_number(p_tenant_id);

        INSERT INTO sales_order (
            tenant_id, order_number, customer_id, status, currency, total_amount
        ) VALUES (
            p_tenant_id, v_order_no, p_customer_id, 'DRAFT', p_currency, 0
        )
        RETURNING order_id INTO v_order_id;

        RETURN v_order_id;
    END create_order;

    --=================================================================
    -- add_order_line
    --
    -- NOTE on p_warehouse_id: This parameter is accepted for API
    -- compatibility but is NOT stored on the order line. The warehouse
    -- resolution happens at confirm_order time, which picks the first
    -- active warehouse for the tenant. This keeps the add_line
    -- operation lightweight and avoids coupling line creation to
    -- warehouse selection. See confirm_order for the actual inventory
    -- reservation logic.
    --=================================================================
    PROCEDURE add_order_line(
        p_tenant_id    IN NUMBER,
        p_order_id     IN NUMBER,
        p_product_id   IN NUMBER,
        p_quantity     IN NUMBER,
        p_warehouse_id IN NUMBER
    ) IS
        v_unit_price NUMBER(18,2);
        v_line_total NUMBER(18,2);
        v_order_status VARCHAR2(20);
    BEGIN
        -- Only DRAFT orders accept new lines
        SELECT status INTO v_order_status
          FROM sales_order
         WHERE order_id = p_order_id AND tenant_id = p_tenant_id
           FOR UPDATE;

        IF v_order_status <> 'DRAFT' THEN
            RAISE_APPLICATION_ERROR(-20023, 'Cannot add lines to a non-DRAFT order.');
        END IF;

        assert_product_tenant(p_tenant_id, p_product_id);

        -- Fetch price (tenant-scoped product)
        SELECT unit_price INTO v_unit_price
          FROM product
         WHERE product_id = p_product_id AND tenant_id = p_tenant_id;

        v_line_total := v_unit_price * p_quantity;

        INSERT INTO sales_order_line (
            tenant_id, order_id, product_id, quantity, unit_price, line_total
        ) VALUES (
            p_tenant_id, p_order_id, p_product_id, p_quantity, v_unit_price, v_line_total
        );

        -- Recalculate order total and transition DRAFT -> PENDING (BR-004)
        UPDATE sales_order
           SET total_amount = (
                   SELECT NVL(SUM(line_total), 0)
                     FROM sales_order_line
                    WHERE order_id = p_order_id AND tenant_id = p_tenant_id
               ),
               status = CASE WHEN status = 'DRAFT' THEN 'PENDING' ELSE status END,
               updated_at = SYSTIMESTAMP,
               version = version + 1
         WHERE order_id = p_order_id AND tenant_id = p_tenant_id;

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20024, 'Order not found: ' || p_order_id);
    END add_order_line;

    --=================================================================
    -- confirm_order - THE atomic core transaction (SRS 37, 61)
    --=================================================================
    PROCEDURE confirm_order(
        p_tenant_id    IN NUMBER,
        p_order_id     IN NUMBER,
        p_actor        IN VARCHAR2 DEFAULT 'APP_JAVA',
        p_correlation_id IN VARCHAR2 DEFAULT NULL
    ) IS
        v_status      VARCHAR2(20);
        v_customer_id NUMBER;
        v_line_count  NUMBER;
        CURSOR c_lines IS
            SELECT product_id, quantity
              FROM sales_order_line
             WHERE order_id = p_order_id AND tenant_id = p_tenant_id
             ORDER BY product_id;
        v_warehouse_id NUMBER;
    BEGIN
        -- Lock the order row first to serialize confirmations
        SELECT status, customer_id
          INTO v_status, v_customer_id
          FROM sales_order
         WHERE order_id = p_order_id AND tenant_id = p_tenant_id
           FOR UPDATE;

        -- Guard: confirm transitions submit from PENDING (FR-008)
        IF v_status <> 'PENDING' THEN
            RAISE_APPLICATION_ERROR(-20025, 'Order cannot be confirmed from status ' || v_status);
        END IF;

        -- BR-004: must have lines
        SELECT COUNT(*) INTO v_line_count
          FROM sales_order_line
         WHERE order_id = p_order_id AND tenant_id = p_tenant_id;

        IF v_line_count = 0 THEN
            RAISE_APPLICATION_ERROR(-20026, 'Order has no lines - cannot confirm.');
        END IF;

        -- Resolve warehouse for inventory reservation.
        -- Design decision: use the first active warehouse for the tenant
        -- rather than per-line warehouse. The p_warehouse_id parameter
        -- in add_order_line is reserved for future multi-warehouse support.
        -- See SRS §37 for inventory reservation requirements.
        SELECT warehouse_id INTO v_warehouse_id
          FROM (
            SELECT warehouse_id
              FROM warehouse
             WHERE tenant_id = p_tenant_id AND status = 'ACTIVE'
             ORDER BY warehouse_id
          )
         WHERE ROWNUM = 1;

        -- Reserve inventory for each line (atomic, locks rows)
        FOR r IN c_lines LOOP
            pkg_inventory.reserve_stock(
                p_tenant_id    => p_tenant_id,
                p_warehouse_id => v_warehouse_id,
                p_product_id   => r.product_id,
                p_quantity     => r.quantity
            );
        END LOOP;

        -- Transition DRAFT -> PENDING -> CONFIRMED
        transition_status(
            p_tenant_id   => p_tenant_id,
            p_order_id    => p_order_id,
            p_old_status  => v_status,
            p_new_status  => 'CONFIRMED',
            p_actor       => p_actor,
            p_correlation => p_correlation_id
        );

        -- Write outbox event (SRS 63) - SAME transaction
        write_outbox(
            p_tenant_id      => p_tenant_id,
            p_aggregate_type => 'SALES_ORDER',
            p_aggregate_id   => TO_CHAR(p_order_id),
            p_event_type     => 'ORDER_CONFIRMED',
            p_payload        => '{"orderId":' || p_order_id || ',"customerId":' || v_customer_id || ',"actor":"' || p_actor || '"}'
        );

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20024, 'Order or warehouse not found for order ' || p_order_id);
    END confirm_order;

    --=================================================================
    -- cancel_order
    --=================================================================
    PROCEDURE cancel_order(
        p_tenant_id    IN NUMBER,
        p_order_id     IN NUMBER,
        p_reason       IN VARCHAR2 DEFAULT NULL,
        p_actor        IN VARCHAR2 DEFAULT 'APP_JAVA',
        p_correlation_id IN VARCHAR2 DEFAULT NULL
    ) IS
        v_status VARCHAR2(20);
        v_warehouse_id NUMBER;
        CURSOR c_lines IS
            SELECT product_id, quantity
              FROM sales_order_line
             WHERE order_id = p_order_id AND tenant_id = p_tenant_id
             ORDER BY product_id;
    BEGIN
        SELECT status INTO v_status
          FROM sales_order
         WHERE order_id = p_order_id AND tenant_id = p_tenant_id
           FOR UPDATE;

        -- BR-008: completed cannot be cancelled directly
        IF v_status IN ('COMPLETED','CANCELLED') THEN
            RAISE_APPLICATION_ERROR(-20027, 'Order ' || v_status || ' cannot be cancelled.');
        END IF;

        -- If previously confirmed/allocated, release inventory
        IF v_status IN ('CONFIRMED','ALLOCATED','PAID','SHIPPED') THEN
            SELECT warehouse_id INTO v_warehouse_id
              FROM (
                SELECT warehouse_id
                  FROM warehouse
                 WHERE tenant_id = p_tenant_id AND status = 'ACTIVE'
                 ORDER BY warehouse_id
              )
             WHERE ROWNUM = 1;

            FOR r IN c_lines LOOP
                pkg_inventory.release_stock(
                    p_tenant_id    => p_tenant_id,
                    p_warehouse_id => v_warehouse_id,
                    p_product_id   => r.product_id,
                    p_quantity     => r.quantity
                );
            END LOOP;
        END IF;

        -- Transition to CANCELLED
        transition_status(
            p_tenant_id   => p_tenant_id,
            p_order_id    => p_order_id,
            p_old_status  => v_status,
            p_new_status  => 'CANCELLED',
            p_actor       => p_actor,
            p_correlation => p_correlation_id,
            p_reason      => p_reason
        );

        write_outbox(
            p_tenant_id      => p_tenant_id,
            p_aggregate_type => 'SALES_ORDER',
            p_aggregate_id   => TO_CHAR(p_order_id),
            p_event_type     => 'ORDER_CANCELLED',
            p_payload        => '{"orderId":' || p_order_id || ',"reason":"' || NVL(p_reason,'') || '"}'
        );

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20024, 'Order not found: ' || p_order_id);
    END cancel_order;

    --=================================================================
    -- register_payment
    --=================================================================
    PROCEDURE register_payment(
        p_tenant_id       IN NUMBER,
        p_order_id        IN NUMBER,
        p_payment_ref     IN VARCHAR2,
        p_amount          IN NUMBER,
        p_currency        IN VARCHAR2,
        p_payment_method  IN VARCHAR2,
        p_actor           IN VARCHAR2 DEFAULT 'APP_JAVA',
        p_correlation_id  IN VARCHAR2 DEFAULT NULL
    ) IS
        v_status VARCHAR2(20);
    BEGIN
        SELECT status INTO v_status
          FROM sales_order
         WHERE order_id = p_order_id AND tenant_id = p_tenant_id
           FOR UPDATE;

        -- Guard: order must be at least CONFIRMED before payment (FR-011)
        IF v_status NOT IN ('CONFIRMED','ALLOCATED','PAID') THEN
            RAISE_APPLICATION_ERROR(-20028, 'Payment requires order CONFIRMED or later, current=' || v_status);
        END IF;

        -- Insert payment (idempotent by unique payment_reference) (FR-014)
        BEGIN
            INSERT INTO payment (
                tenant_id, order_id, payment_reference, amount, currency,
                status, payment_method
            ) VALUES (
                p_tenant_id, p_order_id, p_payment_ref, p_amount, p_currency,
                'CAPTURED', p_payment_method
            );
        EXCEPTION
            WHEN DUP_VAL_ON_INDEX THEN
                RAISE_APPLICATION_ERROR(-20029, 'Duplicate payment reference: ' || p_payment_ref);
        END;

        -- Transition to PAID
        transition_status(
            p_tenant_id   => p_tenant_id,
            p_order_id    => p_order_id,
            p_old_status  => v_status,
            p_new_status  => 'PAID',
            p_actor       => p_actor,
            p_correlation => p_correlation_id
        );

        write_outbox(
            p_tenant_id      => p_tenant_id,
            p_aggregate_type => 'SALES_ORDER',
            p_aggregate_id   => TO_CHAR(p_order_id),
            p_event_type     => 'ORDER_PAID',
            p_payload        => '{"orderId":' || p_order_id || ',"ref":"' || p_payment_ref || '"}'
        );

    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20024, 'Order not found: ' || p_order_id);
    END register_payment;

END pkg_order_management;
/

-- Grant execute to runtime user (least privilege, SRS 58/59)
GRANT EXECUTE ON pkg_order_management TO app_runtime;
GRANT EXECUTE ON pkg_order_management TO app_reporting;
</content>