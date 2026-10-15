CREATE TABLE promo_code (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    discount_type VARCHAR(20) NOT NULL CHECK (discount_type IN ('FIXED_AMOUNT', 'PERCENT')),
    discount_value NUMERIC(12,2) NOT NULL CHECK (discount_value > 0),
    max_discount_amount NUMERIC(12,0) CHECK (max_discount_amount > 0),
    min_order_amount NUMERIC(12,0) NOT NULL DEFAULT 0 CHECK (min_order_amount >= 0),
    valid_from TIMESTAMPTZ,
    valid_to TIMESTAMPTZ,
    usage_limit INTEGER CHECK (usage_limit > 0),
    used_count INTEGER NOT NULL DEFAULT 0 CHECK (used_count >= 0),
    max_uses_per_msisdn INTEGER CHECK (max_uses_per_msisdn > 0),
    applies_to_all_plans BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_promo_code UNIQUE (code),
    CONSTRAINT ck_promo_code_norm CHECK (code = upper(btrim(code)) AND code ~ '^[A-Z0-9_-]{2,50}$'),
    CONSTRAINT ck_promo_percent CHECK (discount_type <> 'PERCENT' OR discount_value <= 100),
    CONSTRAINT ck_promo_fixed_whole CHECK (discount_type <> 'FIXED_AMOUNT' OR discount_value = trunc(discount_value)),
    CONSTRAINT ck_promo_cap_percent_only CHECK (max_discount_amount IS NULL OR discount_type = 'PERCENT'),
    CONSTRAINT ck_promo_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_promo_usage CHECK (usage_limit IS NULL OR used_count <= usage_limit)
);
CREATE TABLE promo_code_plan (
    promo_code_id BIGINT NOT NULL REFERENCES promo_code(id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES plan(id) ON DELETE CASCADE,
    PRIMARY KEY (promo_code_id, plan_id)
);
CREATE INDEX idx_promo_code_plan_plan ON promo_code_plan(plan_id);
