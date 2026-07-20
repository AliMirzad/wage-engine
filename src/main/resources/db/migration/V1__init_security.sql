-- =========================================================================
--  V1 : Security base module
-- =========================================================================

-- ---------- tenants ----------
CREATE TABLE tenants (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    code          VARCHAR(50)  NOT NULL,
    national_id   VARCHAR(20),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP,
    created_by    BIGINT,
    updated_by    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_tenant_code UNIQUE (code)
);

-- ---------- roles ----------
CREATE TABLE roles (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT,                             -- NULL => system role
    name          VARCHAR(50)  NOT NULL,
    description   VARCHAR(255),
    system_role   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP,
    created_by    BIGINT,
    updated_by    BIGINT,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_role_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT fk_role_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

-- ---------- role_permissions ----------
CREATE TABLE role_permissions (
    role_id     BIGINT      NOT NULL,
    permission  VARCHAR(50) NOT NULL,
    PRIMARY KEY (role_id, permission),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- ---------- users ----------
CREATE TABLE users (
    id                     BIGSERIAL PRIMARY KEY,
    tenant_id              BIGINT,                    -- NULL => SUPER_ADMIN
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
    CONSTRAINT uk_user_tenant_username UNIQUE (tenant_id, username),
    CONSTRAINT uk_user_tenant_email    UNIQUE (tenant_id, email),
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email    ON users(email);

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

-- ---------- seed a demo tenant + super admin -------------
-- password below = "Admin@1234" (bcrypt, 12 rounds)
INSERT INTO tenants (name, code, active, created_at, version)
VALUES ('Platform', 'platform', TRUE, NOW(), 0);
