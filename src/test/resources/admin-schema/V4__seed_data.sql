INSERT INTO app (code, name) VALUES
    ('TIKTOK', 'TikTok'), ('FACEBOOK', 'Facebook'), ('YOUTUBE', 'YouTube')
ON CONFLICT (code) DO NOTHING;

INSERT INTO plan (code, name, price, duration_months, quota_type, data_quota_mb,
                  cycle_days, voice_minutes, cutoff_policy, throttle_speed_kbps)
VALUES
    ('D3GB', '3GB/ngay', 150000, 1, 'DAILY', 3072, NULL, 100, 'THROTTLE', 512),
    ('C10GB', '10GB/30 ngay', 90000, 6, 'PER_CYCLE', 10240, 30, 0, 'DISCONNECT', NULL)
ON CONFLICT (code) DO NOTHING;

INSERT INTO plan_first_cycle_bonus (plan_id, bonus_type, amount)
SELECT p.id, seed.bonus_type, seed.amount
FROM plan p
JOIN (VALUES ('D3GB', 'DATA_MB', 5120), ('D3GB', 'VOICE_MIN', 50))
    AS seed(plan_code, bonus_type, amount) ON seed.plan_code = p.code
ON CONFLICT (plan_id, bonus_type) DO NOTHING;

INSERT INTO plan_app_quota (plan_id, app_id, quota_type, quota_mb)
SELECT p.id, a.id, seed.quota_type, seed.quota_mb
FROM (VALUES ('D3GB', 'TIKTOK', 'DAILY', 1024),
             ('D3GB', 'FACEBOOK', 'DAILY', 512),
             ('C10GB', 'YOUTUBE', 'MONTHLY', 2048))
    AS seed(plan_code, app_code, quota_type, quota_mb)
JOIN plan p ON p.code = seed.plan_code
JOIN app a ON a.code = seed.app_code
ON CONFLICT (plan_id, app_id) DO NOTHING;
