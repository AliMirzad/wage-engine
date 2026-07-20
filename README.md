# Accounting Security Base Module

Spring Boot 4.1.0 + Spring Security 7 + JWT + PostgreSQL security foundation for a multi-tenant SaaS accounting app.

## Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 14+

## Setup

1. Create the database:
```sql
CREATE USER accounting_user WITH PASSWORD 'accounting_pass';
CREATE DATABASE accounting_db OWNER accounting_user;
```

2. (Optional) tweak `application.yml`:
    - `app.security.jwt.secret` — **must** be changed before production (min 32 bytes)
    - `app.security.super-admin.password` — first-run admin password
    - `spring.datasource.*` — DB connection

3. Run:
```bash
./mvnw spring-boot:run
```
Flyway will create all tables, `DefaultRoles` seeds the 6 system roles, `SuperAdminSeeder` creates the first admin.

## API Quickstart

### 1. Login as super-admin
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"admin","password":"ChangeMe@123"}'
```
Response:
```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 1, "tenantId": null, "username": "admin",
    "roles": ["SUPER_ADMIN"],
    "permissions": ["USER_READ","USER_WRITE", ...]
  }
}
```

### 2. Call a protected endpoint
```bash
curl http://localhost:8080/api/v1/employees \
  -H "Authorization: Bearer <accessToken>"
```

### 3. Refresh
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
```

### 4. Forgot / reset
```bash
curl -X POST http://localhost:8080/api/v1/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com"}'
# Reset token is logged to console (TODO: hook up email)

curl -X POST http://localhost:8080/api/v1/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"token":"<token-from-log>","newPassword":"NewPass1"}'
```

## How to Protect Your Own Endpoints

The whole point of permission-based security: check permissions directly.

```java
@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    @GetMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_READ')")
    public List<EmployeeDto> list() { ... }

    @PostMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_WRITE')")
    public EmployeeDto create(...) { ... }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_DELETE')")
    public void delete(@PathVariable Long id) { ... }
}
```

## Getting the Current Tenant/User in Your Code

**Option A** — inject via `@AuthenticationPrincipal`:
```java
@GetMapping("/me")
public UserDto me(@AuthenticationPrincipal AuthenticatedPrincipal p) {
    Long userId = p.userId();
    Long tenantId = p.tenantId();
    ...
}
```

**Option B** — anywhere in code via `TenantContext`:
```java
Long tenantId = TenantContext.getTenantId();
```
`TenantContext` is populated by `JwtAuthenticationFilter` and cleared at end of request.

## Making Your Entities Tenant-Aware

Implement `TenantAware` and add a `tenant_id` column. On save, set it from `TenantContext.getTenantId()`.
On read, always filter by tenant — either manually in repositories, or with a Hibernate `@Filter` for automatic isolation (recommended for a real deployment).

## System Roles

| Role            | Purpose                                          |
|-----------------|--------------------------------------------------|
| `SUPER_ADMIN`   | Platform-level (you, the vendor)                 |
| `COMPANY_ADMIN` | Owner of a single company/tenant                 |
| `ACCOUNTANT`    | Day-to-day payroll work                          |
| `MANAGER`       | Read-only + payroll approval                     |
| `EMPLOYEE`      | Sees only their own payslip                      |
| `AUDITOR`       | Read-only across financial data                  |

Custom roles can be created per-tenant later (the `roles` table already supports `tenant_id`).

## Security Features Enabled

- BCrypt password hashing (12 rounds)
- JWT access token (15 min) + rotating refresh token (7 days) with jti revocation
- Brute-force protection: 5 failed attempts → 15 min lock
- Password rules: min 8 chars, at least 1 letter + 1 digit (customizable via `AppSecurityProperties`)
- Password reset via one-time SHA-256-hashed token (30 min TTL)
- Stateless (no HTTP session)
- Audit log (async) for auth events + admin actions
- CORS disabled (per project spec, enable later per environment)

## Directory Layout

```
src/main/java/com/accounting/security/
├── config/       Security wiring, properties, auditor
├── tenant/       Multi-tenancy primitives
├── user/         User entity + Spring Security bridge
├── role/         Role, Permission, default seeder
├── auth/         Login/register/refresh/reset controllers + service
├── jwt/          JWT filter, service, entry points
├── token/        Refresh & password-reset token stores
├── loginattempt/ Brute-force tracker
├── audit/        Audit log entity + async service
├── exception/    Business exceptions + global handler
└── common/       BaseEntity (audit columns + version)
```

## What's Next (Beyond This Base)

- Hook up an email sender for password reset & welcome emails
- Add `@Filter`-based Hibernate tenant filter for automatic row-level isolation
- Add optional 2FA (TOTP)
- Add rate limiter on `/auth/*` (Bucket4j)
- OpenAPI / Swagger (already whitelisted in `SecurityConfig`)
