-- =========================================================================
--  V6 : SUPER_ADMIN از PROJECT_* و EMPLOYEE_* محروم بود.
--  دلیل: DefaultRoles seed-once است. نقش SUPER_ADMIN قبل از افزودن این
--        permissionها به enum ساخته شده بود، پس هرگز دریافتشان نکرد.
--  اثر: SUPER_ADMIN روی /api/v1/projects و /api/v1/employees خطای 403 می‌گرفت.
-- =========================================================================

INSERT INTO role_permissions (role_id, permission)
SELECT r.id, p.permission
FROM roles r
         CROSS JOIN (VALUES
                         ('PROJECT_READ'), ('PROJECT_WRITE'),
                         ('EMPLOYEE_READ'), ('EMPLOYEE_WRITE'), ('EMPLOYEE_DELETE')
) AS p(permission)
WHERE r.name = 'SUPER_ADMIN' AND r.tenant_id IS NULL
    ON CONFLICT (role_id, permission) DO NOTHING;