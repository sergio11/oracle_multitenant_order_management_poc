--=====================================================================
-- V006 - PAYMENT
--
-- Payment domain (SRS 26). Simplified mock of payment processing to
-- demonstrate transactionality and idempotency. No real bank integration.
--=====================================================================
CREATE TABLE payment (
    payment_id        NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         NUMBER          NOT NULL,
    order_id          NUMBER          NOT NULL,
    payment_reference VARCHAR2(100)   NOT NULL,
    amount            NUMBER(18,2)    NOT NULL,
    currency          VARCHAR2(3)     NOT NULL,
    status            VARCHAR2(20)    NOT NULL,
    payment_method    VARCHAR2(30)    NOT NULL,
    created_at        TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at        TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_payment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT fk_payment_order  FOREIGN KEY (order_id)  REFERENCES sales_order(order_id),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING','AUTHORIZED','CAPTURED','FAILED','REFUNDED')),
    CONSTRAINT ck_payment_method CHECK (payment_method IN ('CARD','BANK_TRANSFER','WALLET','CASH')),
    CONSTRAINT ck_payment_currency CHECK (LENGTH(currency) = 3),
    CONSTRAINT uq_payment_reference UNIQUE (tenant_id, payment_reference)
);

COMMENT ON TABLE payment IS 'Mocked payment processing per order (SRS 26)';
COMMENT ON COLUMN payment.tenant_id IS 'Owner tenant (multi-tenancy)';
COMMENT ON COLUMN payment.payment_reference IS 'Unique idempotency key per tenant';