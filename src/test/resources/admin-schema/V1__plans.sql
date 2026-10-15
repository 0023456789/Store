CREATE TABLE plan (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                VARCHAR(50)  NOT NULL,
    name                VARCHAR(150) NOT NULL,
    description         TEXT,
    price               NUMERIC(12,0) NOT NULL CHECK (price >= 0),
    duration_months     SMALLINT NOT NULL CHECK (duration_months IN (1, 6, 12)),
    quota_type          VARCHAR(20) NOT NULL CHECK (quota_type IN ('DAILY', 'PER_CYCLE', 'MONTHLY')),
    data_quota_mb       BIGINT NOT NULL CHECK (data_quota_mb > 0),
    cycle_days          SMALLINT CHECK (cycle_days > 0),
    voice_minutes       INTEGER NOT NULL DEFAULT 0 CHECK (voice_minutes >= 0),
    cutoff_policy       VARCHAR(20) NOT NULL CHECK (cutoff_policy IN ('DISCONNECT', 'THROTTLE')),
    throttle_speed_kbps INTEGER CHECK (throttle_speed_kbps > 0),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_plan_code UNIQUE (code),
    CONSTRAINT ck_plan_code_norm CHECK (code = upper(btrim(code)) AND code ~ '^[A-Z0-9_-]{2,50}$'),
    CONSTRAINT ck_plan_cycle_days CHECK (
        (quota_type = 'PER_CYCLE' AND cycle_days IS NOT NULL) OR
        (quota_type <> 'PER_CYCLE' AND cycle_days IS NULL)
    ),
    CONSTRAINT ck_plan_cycle_fits CHECK (cycle_days IS NULL OR cycle_days <= duration_months * 30),
    CONSTRAINT ck_plan_throttle CHECK (
        (cutoff_policy = 'THROTTLE' AND throttle_speed_kbps IS NOT NULL) OR
        (cutoff_policy = 'DISCONNECT' AND throttle_speed_kbps IS NULL)
    )
);
CREATE INDEX idx_plan_active_created ON plan (is_active, created_at DESC);

CREATE TABLE plan_first_cycle_bonus (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id     BIGINT NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
    bonus_type  VARCHAR(20) NOT NULL CHECK (bonus_type IN ('DATA_MB', 'VOICE_MIN')),
    amount      BIGINT NOT NULL CHECK (amount > 0),
    CONSTRAINT uq_plan_bonus UNIQUE (plan_id, bonus_type)
);

CREATE TABLE app (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(50) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    is_active  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_code UNIQUE (code),
    CONSTRAINT ck_app_code_norm CHECK (code = upper(btrim(code)) AND code ~ '^[A-Z0-9_-]{2,50}$')
);

CREATE TABLE plan_app_quota (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id     BIGINT NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
    app_id      BIGINT NOT NULL REFERENCES app (id) ON DELETE RESTRICT,
    quota_type  VARCHAR(20) NOT NULL CHECK (quota_type IN ('DAILY', 'PER_CYCLE', 'MONTHLY')),
    quota_mb    BIGINT NOT NULL CHECK (quota_mb > 0),
    CONSTRAINT uq_plan_app UNIQUE (plan_id, app_id)
);
CREATE INDEX idx_plan_app_quota_app ON plan_app_quota (app_id);
