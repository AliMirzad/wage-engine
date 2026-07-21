-- Permission metadata catalog
-- The Permission enum in code is the source of truth for what codes exist.
-- This table stores the human-readable Persian description and UI category.
-- Admin can edit description/category at runtime; enum codes require code change + migration.

CREATE TABLE permissions (
                             id             BIGSERIAL PRIMARY KEY,
                             code           VARCHAR(50) UNIQUE NOT NULL,
                             description_fa VARCHAR(255) NOT NULL,
                             category       VARCHAR(50)  NOT NULL,
                             created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                             updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                             created_by     BIGINT,
                             updated_by     BIGINT,
                             version        BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_permissions_category ON permissions(category);

INSERT INTO permissions (code, description_fa, category) VALUES
                                                             -- Tenant
                                                             ('TENANT_READ',        'مشاهده شرکت‌ها',                    'TENANT'),
                                                             ('TENANT_WRITE',       'ایجاد و ویرایش شرکت‌ها',            'TENANT'),
                                                             ('TENANT_DELETE',      'حذف شرکت‌ها',                       'TENANT'),
                                                             -- User
                                                             ('USER_READ',          'مشاهده کاربران',                    'USER'),
                                                             ('USER_WRITE',         'ایجاد و ویرایش کاربران',            'USER'),
                                                             ('USER_DELETE',        'حذف کاربران',                       'USER'),
                                                             -- Role
                                                             ('ROLE_READ',          'مشاهده نقش‌ها و دسترسی‌ها',          'ROLE'),
                                                             ('ROLE_WRITE',         'مدیریت نقش‌ها و تخصیص دسترسی‌ها',    'ROLE'),
                                                             -- Project
                                                             ('PROJECT_READ',       'مشاهده پروژه‌ها',                    'PROJECT'),
                                                             ('PROJECT_WRITE',      'ایجاد و ویرایش پروژه‌ها',            'PROJECT'),
                                                             -- Employee
                                                             ('EMPLOYEE_READ',      'مشاهده کارمندان',                   'EMPLOYEE'),
                                                             ('EMPLOYEE_WRITE',     'ایجاد و ویرایش کارمندان',           'EMPLOYEE'),
                                                             ('EMPLOYEE_DELETE',    'حذف کارمندان',                      'EMPLOYEE'),
                                                             -- Contract
                                                             ('CONTRACT_READ',      'مشاهده قراردادها',                  'CONTRACT'),
                                                             ('CONTRACT_WRITE',     'ایجاد و ویرایش قراردادها',          'CONTRACT'),
                                                             -- Performance
                                                             ('PERFORMANCE_READ',   'مشاهده کارکرد ماهانه',              'PERFORMANCE'),
                                                             ('PERFORMANCE_WRITE',  'ثبت و ویرایش کارکرد ماهانه',        'PERFORMANCE'),
                                                             -- Payroll
                                                             ('PAYROLL_CALCULATE',  'محاسبه حقوق و دستمزد',              'PAYROLL'),
                                                             ('PAYROLL_READ',       'مشاهده حقوق و دستمزد',              'PAYROLL'),
                                                             ('PAYROLL_APPROVE',    'تایید نهایی حقوق و دستمزد',         'PAYROLL'),
                                                             -- Payslip
                                                             ('PAYSLIP_READ_OWN',   'مشاهده فیش حقوقی شخصی',             'PAYSLIP'),
                                                             ('PAYSLIP_READ_ALL',   'مشاهده فیش حقوقی همه کارمندان',     'PAYSLIP'),
                                                             ('PAYSLIP_PRINT',      'چاپ فیش حقوقی',                     'PAYSLIP'),
                                                             -- Bank
                                                             ('BANK_FILE_EXPORT',   'خروجی فایل بانک (پایا/ساتنا)',      'BANK'),
                                                             -- Settings
                                                             ('SETTINGS_READ',      'مشاهده تنظیمات و قوانین حقوق',      'SETTINGS'),
                                                             ('SETTINGS_WRITE',     'ویرایش تنظیمات و قوانین حقوق',      'SETTINGS'),
                                                             -- Report
                                                             ('REPORT_READ',        'مشاهده گزارش‌ها',                   'REPORT'),
                                                             ('REPORT_EXPORT',      'خروجی گزارش‌ها',                    'REPORT'),
                                                             -- Audit
                                                             ('AUDIT_LOG_READ',     'مشاهده لاگ حسابرسی',                'AUDIT');
