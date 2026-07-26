-- =========================================================================
--  V7 : فیلدهای کسب‌وکاری Tenant
--  همه optional — در لحظه ثبت شرکت ممکن است در دست نباشند، ولی پیش از
--  صدور فایل بانکی / لیست بیمه لازم می‌شوند (اعتبارسنجی در همان مرحله).
-- =========================================================================

ALTER TABLE tenants ADD COLUMN IF NOT EXISTS insurance_workshop_code VARCHAR(20);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS economic_code           VARCHAR(20);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS iban                    VARCHAR(26);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS address                 VARCHAR(500);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS phone                   VARCHAR(20);