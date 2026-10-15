-- Run ONCE by a DBA on the existing (admin) database. It adds a login role only:
-- no table, column, index or row of the admin database is changed.
-- The storefront connects with this role to READ the plan catalog.

CREATE ROLE store_readonly LOGIN PASSWORD 'change-me-before-use';   -- replace the password

REVOKE ALL ON SCHEMA public FROM store_readonly;
GRANT USAGE ON SCHEMA public TO store_readonly;

-- whitelist: only what the public catalog needs (never staff_user, refresh_token, promo targets...)
GRANT SELECT ON plan, plan_first_cycle_bonus, plan_app_quota, app,
                plan_voice_allowance, plan_voice_allowance_network, network
    TO store_readonly;

-- belt and braces: even a mistaken write is refused by the session itself
ALTER ROLE store_readonly SET default_transaction_read_only = on;
