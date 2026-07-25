-- =========================================================================
--  V5 : username/email → global unique + case-insensitive
--  دلیل: uk_user_tenant_username با tenant_id NULL بی‌اثر بود (NULLها distinct)
--        و findByUsername روی username تکراری بین tenantها می‌شکست.
--  idempotent نوشته شده — اجرای مجدد بی‌خطر است.
-- =========================================================================

UPDATE users SET username = LOWER(TRIM(username)),
                 email    = LOWER(TRIM(email));

ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_user_tenant_username;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_user_tenant_email;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_username_lower ON users (LOWER(username));
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_email_lower    ON users (LOWER(email));

DROP INDEX IF EXISTS idx_users_username;
DROP INDEX IF EXISTS idx_users_email;

ALTER TABLE roles DROP CONSTRAINT IF EXISTS uk_role_tenant_name;
CREATE UNIQUE INDEX IF NOT EXISTS uk_roles_name_system
    ON roles (name) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_roles_name_tenanted
    ON roles (tenant_id, name) WHERE tenant_id IS NOT NULL;