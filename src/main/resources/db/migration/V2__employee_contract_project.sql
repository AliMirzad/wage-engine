-- =========================================================================
--  V2 : Employee & Contract Management
--  Adds Project, Employee, Contract entities. All soft-deletable / voidable.
--  No hard delete anywhere — history is preserved for legal/audit purposes.
-- =========================================================================

-- ---------- projects ----------
CREATE TABLE projects (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL,
    name          VARCHAR(255) NOT NULL,
    code          VARCHAR(64)  NOT NULL,
    description   TEXT,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    archived_at   TIMESTAMP,
    archived_by   BIGINT,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP,
    created_by    BIGINT,
    updated_by    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_project_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT uk_project_tenant_code UNIQUE (tenant_id, code)
);
CREATE INDEX idx_projects_tenant_active ON projects(tenant_id, active);

-- ---------- employees ----------
CREATE TABLE employees (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL,
    personnel_code    VARCHAR(32)  NOT NULL,
    first_name        VARCHAR(100) NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    national_id       VARCHAR(10)  NOT NULL,
    birth_date        DATE         NOT NULL,
    hire_date         DATE         NOT NULL,
    phone_number      VARCHAR(20),
    email             VARCHAR(255),
    children_count    INTEGER      NOT NULL DEFAULT 0,
    iban              VARCHAR(26),
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at        TIMESTAMP,
    deleted_by        BIGINT,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP,
    created_by        BIGINT,
    updated_by        BIGINT,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_employee_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT uk_employee_tenant_personnel_code UNIQUE (tenant_id, personnel_code),
    CONSTRAINT chk_employee_children_nonneg CHECK (children_count >= 0)
);

-- Partial unique: national_id must be unique among non-deleted rows.
-- A previously-deleted employee's national_id may be re-used if that person
-- is re-hired (a new active record is created).
CREATE UNIQUE INDEX uk_employee_tenant_national_id_active
    ON employees(tenant_id, national_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_employees_tenant_deleted ON employees(tenant_id, deleted_at);
CREATE INDEX idx_employees_tenant_active  ON employees(tenant_id, active);

-- ---------- contracts ----------
CREATE TABLE contracts (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT        NOT NULL,
    employee_id           BIGINT        NOT NULL,
    project_id            BIGINT        NOT NULL,
    contract_number       VARCHAR(64)   NOT NULL,
    base_salary           NUMERIC(19,4) NOT NULL,
    housing_allowance     NUMERIC(19,4) NOT NULL DEFAULT 0,
    food_allowance        NUMERIC(19,4) NOT NULL DEFAULT 0,
    child_allowance_base  NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency              VARCHAR(3)    NOT NULL DEFAULT 'IRR',
    start_date            DATE          NOT NULL,
    end_date              DATE,
    terms                 JSONB,
    notes                 TEXT,
    voided                BOOLEAN       NOT NULL DEFAULT FALSE,
    voided_at             TIMESTAMP,
    voided_by             BIGINT,
    void_reason           TEXT,
    created_at            TIMESTAMP     NOT NULL,
    updated_at            TIMESTAMP,
    created_by            BIGINT,
    updated_by            BIGINT,
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_contract_tenant   FOREIGN KEY (tenant_id)   REFERENCES tenants(id)   ON DELETE RESTRICT,
    CONSTRAINT fk_contract_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_contract_project  FOREIGN KEY (project_id)  REFERENCES projects(id)  ON DELETE RESTRICT,
    CONSTRAINT uk_contract_tenant_number UNIQUE (tenant_id, contract_number),
    CONSTRAINT chk_contract_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT chk_contract_base_salary_nonneg CHECK (base_salary >= 0),
    CONSTRAINT chk_contract_allowances_nonneg CHECK (
        housing_allowance    >= 0 AND
        food_allowance       >= 0 AND
        child_allowance_base >= 0
    )
);
CREATE INDEX idx_contracts_employee_project ON contracts(tenant_id, employee_id, project_id, voided);
CREATE INDEX idx_contracts_date_range       ON contracts(tenant_id, start_date, end_date);
CREATE INDEX idx_contracts_project          ON contracts(tenant_id, project_id);
