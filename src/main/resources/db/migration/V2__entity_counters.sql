-- =========================================================================
--  V2 : شمارنده‌های ازای موجودیت به‌ازای شرکت
--
--  شماره‌های خودتولید payroll (کد پرسنلی، شماره قرارداد و ...) قبلاً با
--  SELECT MAX(...)+1 محاسبه می‌شدند که در همزمانی race می‌داد. جدول زیر
--  با UPSERT + RETURNING به‌صورت اتمی یک شماره برمی‌گرداند و به constraint
--  یکتای هر جدول به‌عنوان safety-net نیاز کمتری داریم.
--
--  ستون current_value: آخرین شماره‌ای که به موجودیتی تخصیص یافته است.
--  مقدار ۰ یعنی هنوز شماره‌ای صادر نشده.
-- =========================================================================

CREATE TABLE entity_counters (
    tenant_id     BIGINT       NOT NULL,
    entity_type   VARCHAR(32)  NOT NULL,
    current_value BIGINT       NOT NULL DEFAULT 0,

    PRIMARY KEY (tenant_id, entity_type),
    CONSTRAINT fk_entity_counters_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE CASCADE
);

-- مقداردهی اولیه بر اساس داده موجود تا شماره‌های بعدی از جای درست شروع شوند.
-- در نصب تازه هیچ ردیفی درج نمی‌شود؛ در نصب قدیمی، بیشترین شماره تخصیص‌یافته
-- به‌عنوان current_value ذخیره می‌شود.
--
-- برای مقاومت در برابر داده legacy با هر فرمتی (بدون بخش سوم، non-numeric،
-- کاراکترهای عجیب و غیره)، از regex برای استخراج فقط دنباله ارقام انتهایی
-- استفاده می‌کنیم. رکوردهایی که با ارقام تمام نمی‌شوند نادیده گرفته می‌شوند —
-- برای شمارنده مهم نیستند، counter برای رکوردهای بعدی از ۰+۱ شروع می‌کند و
-- constraint UNIQUE هر تصادف احتمالی را می‌گیرد.
INSERT INTO entity_counters (tenant_id, entity_type, current_value)
SELECT tenant_id,
       'EMPLOYEE',
       COALESCE(MAX(CAST((regexp_match(personnel_code, '(\d+)$'))[1] AS INTEGER)), 0)
FROM employees
WHERE personnel_code ~ '\d+$'
GROUP BY tenant_id
ON CONFLICT (tenant_id, entity_type) DO NOTHING;

INSERT INTO entity_counters (tenant_id, entity_type, current_value)
SELECT tenant_id,
       'CONTRACT',
       COALESCE(MAX(CAST((regexp_match(contract_number, '(\d+)$'))[1] AS INTEGER)), 0)
FROM contracts
WHERE contract_number ~ '\d+$'
GROUP BY tenant_id
ON CONFLICT (tenant_id, entity_type) DO NOTHING;
