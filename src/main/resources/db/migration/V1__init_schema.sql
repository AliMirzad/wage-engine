-- =========================================================================
--  V1 : اسکیمای پایه سامانه دستمزد مناز
--
--  این فایل تجمیع مهاجرت‌های V1..V14 قبلی است. به‌جای بازسازی تاریخچه
--  تغییرات، وضعیت نهایی را مستقیماً می‌سازد.
--
--  قواعد پایه:
--   * هیچ حذف سختی وجود ندارد — تاریخچه برای الزامات قانونی و حسابرسی می‌ماند
--   * تمام مبالغ NUMERIC(19,4) — هرگز double/float
--   * username و email در کل سامانه یکتا و غیرحساس به بزرگی حروف‌اند
--   * نقش‌ها و کاربر ادمین توسط CommandLineRunnerها ساخته می‌شوند، نه اینجا
-- =========================================================================

-- =========================================================================
--  بخش ۱ : امنیت و چندمستأجری
-- =========================================================================

-- ---------- tenants (شرکت‌ها) ----------
CREATE TABLE tenants (
    id                      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(200) NOT NULL,
    code                    VARCHAR(50)  NOT NULL,
    national_id             VARCHAR(20),
    -- کد کارگاه تأمین اجتماعی — برای ارسال لیست بیمه
    insurance_workshop_code VARCHAR(20),
    -- کد اقتصادی / شناسه مالیاتی — برای لیست مالیات حقوق
    economic_code           VARCHAR(20),
    -- شبای شرکت — مبدأ پرداخت در فایل پایا/ساتنا
    iban                    VARCHAR(26),
    address                 VARCHAR(500),
    phone                   VARCHAR(20),
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_tenant_code UNIQUE (code)
);

-- ---------- roles ----------
-- tenant_id = NULL یعنی نقش سیستمی (قابل مشاهده برای همه شرکت‌ها)
CREATE TABLE roles (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT,
    name          VARCHAR(50)  NOT NULL,
    description   VARCHAR(255),
    system_role   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP,
    created_by    BIGINT,
    updated_by    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_role_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- UNIQUE(tenant_id, name) کار نمی‌کند چون در Postgres مقادیر NULL متمایز
-- شمرده می‌شوند و همه نقش‌های سیستمی tenant_id = NULL دارند.
-- پس دو ایندکس جزئی جداگانه لازم است.
CREATE UNIQUE INDEX uk_roles_name_system   ON roles (name) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX uk_roles_name_tenanted ON roles (tenant_id, name) WHERE tenant_id IS NOT NULL;

-- ---------- role_permissions ----------
CREATE TABLE role_permissions (
    role_id     BIGINT      NOT NULL,
    permission  VARCHAR(50) NOT NULL,
    PRIMARY KEY (role_id, permission),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- ---------- users ----------
-- tenant_id = NULL یعنی SUPER_ADMIN (سطح پلتفرم)
CREATE TABLE users (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              BIGINT,
    username               VARCHAR(100) NOT NULL,
    email                  VARCHAR(150) NOT NULL,
    password_hash          VARCHAR(255) NOT NULL,
    first_name             VARCHAR(100),
    last_name              VARCHAR(100),
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    account_non_locked     BOOLEAN      NOT NULL DEFAULT TRUE,
    locked_until           TIMESTAMP,
    failed_login_attempts  INTEGER      NOT NULL DEFAULT 0,
    last_login_at          TIMESTAMP,
    password_changed_at    TIMESTAMP,
    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP,
    created_by             BIGINT,
    updated_by             BIGINT,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- یکتایی سراسری و غیرحساس به بزرگی حروف.
-- روی LOWER(...) است تا Ali و ali یک حساب شمرده شوند و با
-- findByUsernameIgnoreCase در کد هم‌خوان باشد (همان expression → ایندکس hit).
CREATE UNIQUE INDEX uk_users_username_lower ON users (LOWER(username));
CREATE UNIQUE INDEX uk_users_email_lower    ON users (LOWER(email));

CREATE INDEX idx_users_tenant ON users (tenant_id);

-- ---------- user_roles ----------
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- ---------- refresh_tokens ----------
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    jti         VARCHAR(64)  NOT NULL,
    user_id     BIGINT       NOT NULL,
    tenant_id   BIGINT,
    expires_at  TIMESTAMP    NOT NULL,
    issued_at   TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    revoked_at  TIMESTAMP,
    user_agent  VARCHAR(255),
    ip_address  VARCHAR(45),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX idx_refresh_token_jti  ON refresh_tokens(jti);
CREATE INDEX        idx_refresh_token_user ON refresh_tokens(user_id);

-- ---------- password_reset_tokens ----------
CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token_hash  VARCHAR(128) NOT NULL,
    user_id     BIGINT       NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    used_at     TIMESTAMP,
    CONSTRAINT fk_pwd_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX idx_pwd_reset_token_hash ON password_reset_tokens(token_hash);
CREATE INDEX        idx_pwd_reset_user       ON password_reset_tokens(user_id);

-- ---------- audit_logs ----------
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT,
    user_id     BIGINT,
    username    VARCHAR(100),
    event       VARCHAR(100)  NOT NULL,
    outcome     VARCHAR(20)   NOT NULL,
    target_type VARCHAR(100),
    target_id   VARCHAR(100),
    details     VARCHAR(1000),
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(500),
    created_at  TIMESTAMP     NOT NULL
);
CREATE INDEX idx_audit_user    ON audit_logs(user_id);
CREATE INDEX idx_audit_tenant  ON audit_logs(tenant_id);
CREATE INDEX idx_audit_event   ON audit_logs(event);
CREATE INDEX idx_audit_created ON audit_logs(created_at);

-- =========================================================================
--  بخش ۲ : کاتالوگ دسترسی‌ها
--  enum Permission در کد مرجع نهایی کدهاست. این جدول فقط شرح فارسی و
--  دسته‌بندی را نگه می‌دارد که در زمان اجرا قابل ویرایش است.
--  PermissionCatalogSync هر کد جدید enum را خودکار اینجا درج می‌کند.
-- =========================================================================

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
    -- شرکت‌ها
    ('TENANT_READ',            'مشاهده شرکت‌ها',                    'TENANT'),
    ('TENANT_WRITE',           'ایجاد و ویرایش شرکت‌ها',            'TENANT'),
    ('TENANT_DELETE',          'حذف شرکت‌ها',                       'TENANT'),
    -- کاربران
    ('USER_READ',              'مشاهده کاربران',                    'USER'),
    ('USER_WRITE',             'ایجاد و ویرایش کاربران',            'USER'),
    ('USER_DELETE',            'حذف کاربران',                       'USER'),
    -- نقش‌ها
    ('ROLE_READ',              'مشاهده نقش‌ها و دسترسی‌ها',          'ROLE'),
    ('ROLE_WRITE',             'مدیریت نقش‌ها و تخصیص دسترسی‌ها',    'ROLE'),
    -- پروژه‌ها
    ('PROJECT_READ',           'مشاهده پروژه‌ها',                    'PROJECT'),
    ('PROJECT_WRITE',          'ایجاد و ویرایش پروژه‌ها',            'PROJECT'),
    ('PROJECT_FINANCIAL_READ', 'مشاهده اطلاعات مالی پروژه (مبلغ پیمان)', 'PROJECT'),
    -- کارمندان
    ('EMPLOYEE_READ',          'مشاهده کارمندان',                   'EMPLOYEE'),
    ('EMPLOYEE_WRITE',         'ایجاد و ویرایش کارمندان',           'EMPLOYEE'),
    ('EMPLOYEE_DELETE',        'حذف کارمندان',                      'EMPLOYEE'),
    -- قراردادها
    ('CONTRACT_READ',          'مشاهده قراردادها',                  'CONTRACT'),
    ('CONTRACT_WRITE',         'ایجاد و ویرایش قراردادها',          'CONTRACT'),
    -- کارکرد ماهانه
    ('PERFORMANCE_READ',       'مشاهده کارکرد ماهانه',              'PERFORMANCE'),
    ('PERFORMANCE_WRITE',      'ثبت و ویرایش کارکرد ماهانه',        'PERFORMANCE'),
    -- حقوق و دستمزد
    ('PAYROLL_CALCULATE',      'محاسبه حقوق و دستمزد',              'PAYROLL'),
    ('PAYROLL_READ',           'مشاهده حقوق و دستمزد',              'PAYROLL'),
    ('PAYROLL_APPROVE',        'تایید نهایی حقوق و دستمزد',         'PAYROLL'),
    -- فیش حقوقی
    ('PAYSLIP_READ_OWN',       'مشاهده فیش حقوقی شخصی',             'PAYSLIP'),
    ('PAYSLIP_READ_ALL',       'مشاهده فیش حقوقی همه کارمندان',     'PAYSLIP'),
    ('PAYSLIP_PRINT',          'چاپ فیش حقوقی',                     'PAYSLIP'),
    -- بانک
    ('BANK_FILE_EXPORT',       'خروجی فایل بانک (پایا/ساتنا)',      'BANK'),
    -- تنظیمات
    ('SETTINGS_READ',          'مشاهده تنظیمات و قوانین حقوق',      'SETTINGS'),
    ('SETTINGS_WRITE',         'ویرایش تنظیمات و قوانین حقوق',      'SETTINGS'),
    -- گزارش‌ها
    ('REPORT_READ',            'مشاهده گزارش‌ها',                   'REPORT'),
    ('REPORT_EXPORT',          'خروجی گزارش‌ها',                    'REPORT'),
    -- حسابرسی
    ('AUDIT_LOG_READ',         'مشاهده لاگ حسابرسی',                'AUDIT');

-- =========================================================================
--  بخش ۳ : دامنه کسب‌وکار
-- =========================================================================

-- ---------- projects ----------
-- چرخه حیات: PLANNED / ACTIVE / SUSPENDED / COMPLETED / CANCELLED
-- فقط ACTIVE اجازه ثبت قرارداد جدید می‌دهد.
-- closed_at/closed_by مهر خاتمه یا لغو است؛ حذف سخت وجود ندارد.
CREATE TABLE projects (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              BIGINT       NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    code                   VARCHAR(64)  NOT NULL,
    description            TEXT,
    status                 VARCHAR(20)  NOT NULL,
    -- اطلاعات کارفرما و پیمان
    client_name            VARCHAR(200),
    client_national_id     VARCHAR(20),
    client_contract_number VARCHAR(64),
    client_contract_date   DATE,
    -- مبلغ کل پیمان — محرمانه، نیازمند دسترسی PROJECT_FINANCIAL_READ
    contract_amount        NUMERIC(19,4),
    start_date             DATE         NOT NULL,
    end_date               DATE,
    actual_end_date        DATE,
    location               VARCHAR(255),
    notes                  TEXT,
    closed_at              TIMESTAMP,
    closed_by              BIGINT,
    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP,
    created_by             BIGINT,
    updated_by             BIGINT,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_project_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT uk_project_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_project_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_project_contract_amount_nonneg CHECK (contract_amount IS NULL OR contract_amount >= 0)
);
CREATE INDEX idx_projects_tenant_status ON projects (tenant_id, status);

-- ---------- employees ----------
-- سه محور مستقل وضعیت:
--   active           = توقف موقت (مرخصی طولانی، تعلیق) — قابل برگشت
--   termination_date = ترک کار (استعفا، اخراج) — رکورد می‌ماند برای سنوات و بیمه
--   deleted_at       = اشتباه ثبت شده — از همه کوئری‌های عادی فیلتر می‌شود
CREATE TABLE employees (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               BIGINT       NOT NULL,
    personnel_code          VARCHAR(32)  NOT NULL,
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100) NOT NULL,
    national_id             VARCHAR(10)  NOT NULL,
    birth_date              DATE         NOT NULL,
    hire_date               DATE         NOT NULL,
    phone_number            VARCHAR(20),
    email                   VARCHAR(255),
    children_count          INTEGER      NOT NULL DEFAULT 0,
    -- بانکی — برای تولید فایل پرداخت
    iban                    VARCHAR(26),
    bank_name               VARCHAR(100),
    -- مؤثر در محاسبه حقوق
    marital_status          VARCHAR(20)  NOT NULL,
    insurance_number        VARCHAR(20),
    insurance_type          VARCHAR(20)  NOT NULL,
    employment_type         VARCHAR(20)  NOT NULL,
    job_title               VARCHAR(100),
    -- پرونده پرسنلی
    father_name             VARCHAR(100),
    id_card_number          VARCHAR(20),
    issue_place             VARCHAR(100),
    military_status         VARCHAR(20),
    education_level         VARCHAR(50),
    address                 VARCHAR(500),
    emergency_contact_name  VARCHAR(100),
    emergency_contact_phone VARCHAR(20),
    -- ترک کار (متفاوت با حذف نرم)
    termination_date        DATE,
    termination_reason      VARCHAR(500),
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at              TIMESTAMP,
    deleted_by              BIGINT,
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP,
    created_by              BIGINT,
    updated_by              BIGINT,
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_employee_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT uk_employee_tenant_personnel_code UNIQUE (tenant_id, personnel_code),
    CONSTRAINT chk_employee_children_nonneg CHECK (children_count >= 0),
    CONSTRAINT chk_employee_hire_after_birth CHECK (hire_date >= birth_date),
    CONSTRAINT chk_employee_termination_after_hire
        CHECK (termination_date IS NULL OR termination_date >= hire_date)
);

-- کد ملی فقط بین رکوردهای حذف‌نشده یکتاست، تا اگر رکوردی اشتباهی ثبت و
-- حذف شد، همان شخص دوباره قابل ثبت باشد.
CREATE UNIQUE INDEX uk_employee_tenant_national_id_active
    ON employees(tenant_id, national_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_employees_tenant_deleted ON employees(tenant_id, deleted_at);
CREATE INDEX idx_employees_tenant_active  ON employees(tenant_id, active);

-- ---------- contracts ----------
-- قرارداد یک کارمند را به یک پروژه با شرایط مالی مشخص گره می‌زند.
-- تغییر حقوق یا تاریخ = خاتمه قرارداد فعلی + قرارداد جدید با
-- previous_contract_id — الزام قانون کار و گزارش‌دهی بیمه/مالیات.
CREATE TABLE contracts (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              BIGINT        NOT NULL,
    employee_id            BIGINT        NOT NULL,
    project_id             BIGINT        NOT NULL,
    contract_number        VARCHAR(64)   NOT NULL,
    contract_type          VARCHAR(20)   NOT NULL,
    -- مبنای تفسیر base_salary: MONTHLY / DAILY / HOURLY
    salary_basis           VARCHAR(20)   NOT NULL,
    base_salary            NUMERIC(19,4) NOT NULL,
    housing_allowance      NUMERIC(19,4) NOT NULL DEFAULT 0,
    food_allowance         NUMERIC(19,4) NOT NULL DEFAULT 0,
    child_allowance_base   NUMERIC(19,4) NOT NULL DEFAULT 0,
    transport_allowance    NUMERIC(19,4) NOT NULL DEFAULT 0,
    seniority_pay          NUMERIC(19,4) NOT NULL DEFAULT 0,
    hardship_allowance     NUMERIC(19,4) NOT NULL DEFAULT 0,
    -- مبنای محاسبه اضافه‌کاری. سقف قانونی ۴۴ ساعت (ماده ۵۱ قانون کار)
    working_hours_per_week NUMERIC(5,2),
    currency               VARCHAR(3)    NOT NULL DEFAULT 'IRR',
    start_date             DATE          NOT NULL,
    end_date               DATE,
    signed_date            DATE,
    probation_end_date     DATE,
    job_title              VARCHAR(100),
    -- ظرف انعطاف‌پذیر برای بندهایی که هنوز ستون اختصاصی ندارند
    terms                  JSONB,
    notes                  TEXT,
    -- ابطال (اشتباه در ثبت) — متفاوت با خاتمه طبیعی
    voided                 BOOLEAN       NOT NULL DEFAULT FALSE,
    voided_at              TIMESTAMP,
    voided_by              BIGINT,
    void_reason            TEXT,
    previous_contract_id   BIGINT,
    created_at             TIMESTAMP     NOT NULL,
    updated_at             TIMESTAMP,
    created_by             BIGINT,
    updated_by             BIGINT,
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_contract_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenants(id)   ON DELETE RESTRICT,
    CONSTRAINT fk_contract_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_contract_project  FOREIGN KEY (project_id)  REFERENCES projects(id)  ON DELETE RESTRICT,
    CONSTRAINT fk_contract_previous FOREIGN KEY (previous_contract_id) REFERENCES contracts(id) ON DELETE RESTRICT,
    CONSTRAINT uk_contract_tenant_number UNIQUE (tenant_id, contract_number),
    CONSTRAINT chk_contract_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_contract_base_salary_nonneg CHECK (base_salary >= 0),
    CONSTRAINT chk_contract_allowances_nonneg CHECK (
        housing_allowance    >= 0 AND
        food_allowance       >= 0 AND
        child_allowance_base >= 0 AND
        transport_allowance  >= 0 AND
        seniority_pay        >= 0 AND
        hardship_allowance   >= 0
    ),
    CONSTRAINT chk_contract_weekly_hours CHECK (
        working_hours_per_week IS NULL OR
        (working_hours_per_week > 0 AND working_hours_per_week <= 44)
    )
);

-- هر قرارداد حداکثر یک جانشین دارد — زنجیره خطی می‌ماند
CREATE UNIQUE INDEX uk_contract_previous_unique
    ON contracts(previous_contract_id) WHERE previous_contract_id IS NOT NULL;

CREATE INDEX idx_contracts_employee_project ON contracts(tenant_id, employee_id, project_id, voided);
CREATE INDEX idx_contracts_date_range       ON contracts(tenant_id, start_date, end_date);
CREATE INDEX idx_contracts_tenant_employee  ON contracts(tenant_id, employee_id);
CREATE INDEX idx_contracts_tenant_project   ON contracts(tenant_id, project_id);
CREATE INDEX idx_contracts_previous         ON contracts(previous_contract_id);

-- =========================================================================
--  بخش ۴ : داده اولیه
--  نقش‌های سیستمی و کاربر ادمین توسط CommandLineRunnerها ساخته می‌شوند:
--    PermissionCatalogSync (Order 0) → DefaultRoles (Order 1) → SuperAdminSeeder (Order 2)
-- =========================================================================

INSERT INTO tenants (name, code, active, created_at, version)
VALUES ('Platform', 'platform', TRUE, NOW(), 0);
