-- =========================================================================
--  V5 : حذف permissionهای هرگز استفاده‌نشده
--
--  چرا: USER_DELETE و TENANT_DELETE در کد اعلام شده بودند و به COMPANY_ADMIN
--  اختصاص داشتند ولی هیچ endpointی @PreAuthorize("hasAuthority('USER_DELETE')")
--  یا مشابهش نداشت. کاربر حذف نمی‌شود — فقط deactivate می‌شود (تاریخچه لازم).
--  شرکت هم همینطور. نگه‌داشتن این permissionها فقط توهم دسترسی می‌سازد.
--
--  ترتیب حذف مهم است:
--   1. ابتدا role_permissions cascade نمی‌شود چون constraint روی permission code
--      از نوع FK نیست — role_permissions ستون permission به‌صورت VARCHAR ذخیره شده.
--   2. پس ابتدا از role_permissions حذف کن سپس از permissions.
-- =========================================================================

DELETE FROM role_permissions
WHERE permission IN ('USER_DELETE', 'TENANT_DELETE');

DELETE FROM permissions
WHERE code IN ('USER_DELETE', 'TENANT_DELETE');
