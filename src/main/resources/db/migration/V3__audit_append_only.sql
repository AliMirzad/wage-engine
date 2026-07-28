-- =========================================================================
--  V3 : جدول audit_logs را append-only می‌کند
--
--  چرا: audit_log تنها منبع حقیقت برای بازرسی مالی/قانونی است. یک نشست
--  اپ (حتی SUPER_ADMIN) نباید بتواند رکورد پاک یا ویرایش کند. تنها راه
--  دور زدن این triggerها، بایپس اپ و اتصال مستقیم به DB با privilege
--  حذف/تغییر trigger است — که خارج از مرز اعتماد اپ است و باید در سطح
--  IAM دیتابیس کنترل شود.
--
--  به‌طور خاص TRUNCATE هم مسدود می‌شود؛ در PostgreSQL این نیازمند trigger
--  جداگانه است چون TRUNCATE از UPDATE/DELETE trigger عبور نمی‌کند.
-- =========================================================================

CREATE OR REPLACE FUNCTION audit_logs_reject_modification()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only: % is not permitted', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_no_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_modification();

CREATE TRIGGER audit_logs_no_delete
    BEFORE DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_reject_modification();

CREATE TRIGGER audit_logs_no_truncate
    BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_reject_modification();

-- ستون hash برای زنجیره integrity در آینده. الان NULLable و پر نمی‌شود
-- تا این migration کند نشود؛ افزودن chain کامل در migration بعدی است
-- اگر الزام حسابرسی رسمی داشتید.
ALTER TABLE audit_logs
    ADD COLUMN row_hash BYTEA;
