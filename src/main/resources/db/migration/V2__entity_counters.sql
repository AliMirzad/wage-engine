-- =========================================================================
--  V2 : شمارنده‌های ازای موجودیت به‌ازای شرکت
--
--  شماره‌های خودتولید payroll (کد پرسنلی، شماره قرارداد و ...) قبلاً با
--  SELECT MAX(...)+1 محاسبه می‌شدند که در همزمانی race می‌داد. جدول زیر
--  با UPSERT + RETURNING به‌صورت اتمی یک شماره برمی‌گرداند و به constraint
--  یکتای هر جدول به‌عنوان safety-net تکیه می‌کند.
--
--  ستون current_value: آخرین شماره‌ای که به موجودیتی تخصیص یافته است.
--  مقدار ۰ یعنی هنوز شماره‌ای صادر نشده — روی اولین nextValue می‌شود ۱.
--
--  توجه: این migration عمداً backfill داده موجود نمی‌کند. اگر روی DB شما
--  از قبل employee یا contract وجود دارد و می‌خواهید شمارنده از بیشترین
--  seq فعلی شروع کند، یک migration مجزا (مثلاً V4) بنویسید که با SQL
--  ایمن (مقاوم به فرمت‌های ناسازگار) داده را migrate کند. برای نصب تازه
--  که این پروژه هدفش هست، شروع از ۰ درست است.
-- =========================================================================

CREATE TABLE entity_counters (
    tenant_id     BIGINT       NOT NULL,
    entity_type   VARCHAR(32)  NOT NULL,
    current_value BIGINT       NOT NULL DEFAULT 0,

    PRIMARY KEY (tenant_id, entity_type),
    CONSTRAINT fk_entity_counters_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE CASCADE
);
