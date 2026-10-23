-- V1__store_orders.sql: Schema of the Storefront database
-- Contains store_order, payment_attempt, and payment_event

CREATE TABLE IF NOT EXISTS store_order (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_code      VARCHAR(20)   NOT NULL,
    plan_id         BIGINT        NOT NULL,
    plan_code       VARCHAR(50)   NOT NULL,
    plan_name       VARCHAR(150)  NOT NULL,
    duration_months INTEGER       NOT NULL CHECK (duration_months IN (1, 6, 12)),
    cycle_days      INTEGER       NOT NULL CHECK (cycle_days > 0),
    plan_price      BIGINT        NOT NULL CHECK (plan_price >= 0),
    sim_fee         BIGINT        NOT NULL DEFAULT 0 CHECK (sim_fee >= 0),
    discount        BIGINT        NOT NULL DEFAULT 0 CHECK (discount >= 0),
    amount          BIGINT        NOT NULL CHECK (amount > 0),
    msisdn          VARCHAR(15)   NOT NULL CHECK (msisdn ~ '^[0-9]{9,15}$'),
    customer_name   VARCHAR(150)  NOT NULL,
    customer_phone  VARCHAR(15)   NOT NULL CHECK (customer_phone ~ '^[0-9]{9,15}$'),
    customer_email  VARCHAR(150),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING_PAYMENT'
                    CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'FAILED', 'EXPIRED', 'CANCELLED')),
    expires_at      TIMESTAMPTZ   NOT NULL,
    paid_at         TIMESTAMPTZ,
    activated_at    TIMESTAMPTZ,
    valid_until     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_store_order_code UNIQUE (order_code),
    CONSTRAINT ck_store_order_code CHECK (order_code ~ '^[A-Z0-9]{8,20}$'),
    CONSTRAINT ck_store_order_amount CHECK (amount = plan_price + sim_fee - discount),
    CONSTRAINT ck_store_order_paid CHECK (status <> 'PAID' OR paid_at IS NOT NULL),
    CONSTRAINT ck_store_order_valid CHECK (valid_until IS NULL OR (activated_at IS NOT NULL AND valid_until > activated_at))
);
CREATE INDEX IF NOT EXISTS idx_store_order_status_expires ON store_order (status, expires_at);
CREATE INDEX IF NOT EXISTS idx_store_order_msisdn ON store_order (msisdn);

CREATE TABLE IF NOT EXISTS payment_attempt (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id               BIGINT        NOT NULL REFERENCES store_order (id) ON DELETE RESTRICT,
    txn_ref                VARCHAR(40)   NOT NULL,
    amount                 BIGINT        NOT NULL CHECK (amount > 0),
    vnp_create_date        VARCHAR(14)   NOT NULL CHECK (vnp_create_date ~ '^[0-9]{14}$'),
    vnp_expire_date        VARCHAR(14)   NOT NULL CHECK (vnp_expire_date ~ '^[0-9]{14}$'),
    status                 VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED')),
    vnp_response_code      VARCHAR(2),
    vnp_transaction_status VARCHAR(2),
    vnp_transaction_no     VARCHAR(15),
    vnp_bank_code          VARCHAR(20),
    vnp_card_type          VARCHAR(20),
    vnp_pay_date           VARCHAR(14),
    confirmed_via          VARCHAR(10)   CHECK (confirmed_via IN ('IPN', 'RETURN')),
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_attempt_txn_ref UNIQUE (txn_ref),
    CONSTRAINT ck_payment_attempt_txn_ref CHECK (txn_ref ~ '^[A-Za-z0-9]{1,40}$'),
    CONSTRAINT ck_payment_attempt_success CHECK (status <> 'SUCCESS' OR (vnp_transaction_no IS NOT NULL AND confirmed_via IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_payment_attempt_order ON payment_attempt (order_id);
CREATE INDEX IF NOT EXISTS idx_payment_attempt_pending ON payment_attempt (created_at) WHERE status = 'PENDING';
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_attempt_one_success ON payment_attempt (order_id) WHERE status = 'SUCCESS';

CREATE TABLE IF NOT EXISTS payment_logs (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source             VARCHAR(10)  NOT NULL CHECK (source IN ('IPN', 'RETURN')),
    txn_ref            VARCHAR(100),
    vnp_transaction_no VARCHAR(50),
    amount             BIGINT,
    vnp_response_code  VARCHAR(10),
    vnp_bank_code      VARCHAR(50),
    signature_valid    BOOLEAN      NOT NULL,
    rsp_code           VARCHAR(10),
    raw_payload        JSONB        NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_payment_logs_txn_ref ON payment_logs (txn_ref);
CREATE INDEX IF NOT EXISTS idx_payment_logs_vnp_txn_no ON payment_logs (vnp_transaction_no);
