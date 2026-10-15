CREATE TABLE IF NOT EXISTS plan (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                VARCHAR(50)  NOT NULL UNIQUE,
    name                VARCHAR(150) NOT NULL,
    description         TEXT,
    price               NUMERIC(12,0) NOT NULL,
    duration_months     SMALLINT NOT NULL,
    quota_type          VARCHAR(20) NOT NULL,
    data_quota_mb       BIGINT NOT NULL,
    cycle_days          SMALLINT,
    voice_minutes       INTEGER NOT NULL DEFAULT 0,
    cutoff_policy       VARCHAR(20) NOT NULL,
    throttle_speed_kbps INTEGER,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS plan_first_cycle_bonus (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id     BIGINT NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
    bonus_type  VARCHAR(20) NOT NULL,
    amount      BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS app (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(50) NOT NULL UNIQUE,
    name       VARCHAR(100) NOT NULL,
    is_active  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS plan_app_quota (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id     BIGINT NOT NULL REFERENCES plan (id) ON DELETE CASCADE,
    app_id      BIGINT NOT NULL REFERENCES app (id) ON DELETE RESTRICT,
    quota_type  VARCHAR(20) NOT NULL,
    quota_mb    BIGINT NOT NULL
);

INSERT INTO plan (code, name, description, price, duration_months, quota_type, data_quota_mb, cycle_days, voice_minutes, cutoff_policy, throttle_speed_kbps, is_active)
VALUES ('SKY89', 'Gói Sky89', '3GB/ngày', 89000, 1, 'DAILY', 3072, NULL, 100, 'THROTTLE', 512, TRUE);
