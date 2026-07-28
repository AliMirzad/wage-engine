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
INSERT INTO entity_counters (tenant_id, entity_type, current_value)
SELECT tenant_id,
       'EMPLOYEE',
       COALESCE(MAX(CAST(SPLIT_PART(personnel_code, '-', 3) AS INTEGER)), 0)
FROM employees
GROUP BY tenant_id
ON CONFLICT (tenant_id, entity_type) DO NOTHING;

INSERT INTO entity_counters (tenant_id, entity_type, current_value)
SELECT tenant_id,
       'CONTRACT',
       COALESCE(MAX(CAST(SPLIT_PART(contract_number, '-', 3) AS INTEGER)), 0)
FROM contracts
GROUP BY tenant_id
ON CONFLICT (tenant_id, entity_type) DO NOTHING;
