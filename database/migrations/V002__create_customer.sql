--=====================================================================
-- V002 - CUSTOMER, CUSTOMER_ADDRESS, CUSTOMER_ACCOUNT
--
-- Customer domain (SRS 16, 17, 18).
-- Tenant-aware tables (TENANT_ID present in all tenant entities).
--=====================================================================

--=====================================================================
-- CUSTOMER (SRS 16)
--=====================================================================
CREATE TABLE customer (
    customer_id     NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       NUMBER          NOT NULL,
    customer_number VARCHAR2(30)    NOT NULL,
    first_name      VARCHAR2(100)   NOT NULL,
    last_name       VARCHAR2(100)   NOT NULL,
    email           VARCHAR2(200)   NOT NULL,
    phone           VARCHAR2(30),
    status          VARCHAR2(20)    NOT NULL,
    created_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id),
    CONSTRAINT ck_customer_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED')),
    CONSTRAINT uq_customer_number UNIQUE (tenant_id, customer_number),
    CONSTRAINT uq_customer_email  UNIQUE (tenant_id, email)
);

COMMENT ON TABLE customer IS 'Customer belonging to exactly one tenant (SRS 16)';
COMMENT ON COLUMN customer.tenant_id IS 'Owner tenant (multi-tenancy)';

--=====================================================================
-- CUSTOMER_ADDRESS (SRS 17)
--=====================================================================
CREATE TABLE customer_address (
    address_id      NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       NUMBER          NOT NULL,
    customer_id     NUMBER          NOT NULL,
    address_type    VARCHAR2(20)    NOT NULL,
    street          VARCHAR2(200)   NOT NULL,
    city            VARCHAR2(100)   NOT NULL,
    postal_code     VARCHAR2(20),
    country         VARCHAR2(60)    NOT NULL,
    is_default      NUMBER(1)       DEFAULT 0 NOT NULL,
    created_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_addr_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenant(tenant_id),
    CONSTRAINT fk_addr_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT ck_addr_type CHECK (address_type IN ('BILLING','SHIPPING','OTHER')),
    CONSTRAINT ck_addr_default CHECK (is_default IN (0,1))
);

COMMENT ON TABLE customer_address IS 'Customer addresses (SRS 17)';
COMMENT ON COLUMN customer_address.tenant_id IS 'Owner tenant (multi-tenancy)';

--=====================================================================
-- CUSTOMER_ACCOUNT (SRS 18)
--=====================================================================
CREATE TABLE customer_account (
    account_id      NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id       NUMBER          NOT NULL,
    customer_id     NUMBER          NOT NULL,
    credit_limit    NUMBER(18,2)    NOT NULL,
    current_balance NUMBER(18,2)    NOT NULL,
    currency        VARCHAR2(3)     NOT NULL,
    status          VARCHAR2(20)    NOT NULL,
    created_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at      TIMESTAMP(6)    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_acct_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenant(tenant_id),
    CONSTRAINT fk_acct_customer FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT ck_acct_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED')),
    CONSTRAINT ck_acct_currency CHECK (LENGTH(currency) = 3)
);

COMMENT ON TABLE customer_account IS 'Commercial account info per customer (SRS 18)';
COMMENT ON COLUMN customer_account.tenant_id IS 'Owner tenant (multi-tenancy)';