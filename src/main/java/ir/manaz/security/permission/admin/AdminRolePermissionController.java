package ir.manaz.security.permission.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.manaz.security.jwt.AuthenticatedPrincipal;
import ir.manaz.security.permission.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static ir.manaz.security.permission.admin.AdminRolePermissionDtos.*;

/**
 * Admin endpoints for managing roles and their permissions.
 *
 * <p>All endpoints require {@code ROLE_WRITE} authority (by default only {@code SUPER_ADMIN}).
 *
 * <p><b>Cache note:</b> permission changes take effect only after the affected user
 * logs in again or refreshes their access token (JWT contains permissions at issue time).
 */
@Tag(name = "Admin: Roles & Permissions",
        description = "مدیریت نقش‌ها و تخصیص دسترسی‌ها — فقط برای SUPER_ADMIN")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_WRITE')")
public class AdminRolePermissionController {

    private final AdminRolePermissionService adminService;
    private final PermissionService permissionService;

    // ============================ PERMISSION CATALOG ============================

    @Operation(
            summary = "لیست تمام دسترسی‌های موجود در سیستم",
            description = "برمی‌گرداند: کد، توضیح فارسی و category هر دسترسی. مرتب‌شده بر اساس category."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "لیست دسترسی‌ها"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی ROLE_WRITE ندارید")
    })
    @GetMapping("/permissions")
    public List<PermissionView> listPermissions() {
        return adminService.listPermissions(permissionService.listAll());
    }

    @Operation(
            summary = "ویرایش توضیح یا category یک دسترسی",
            description = "کد دسترسی قابل تغییر نیست؛ فقط متادیتای نمایشی."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "دسترسی به‌روزرسانی شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی ROLE_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "کد دسترسی یافت نشد")
    })
    @PatchMapping("/permissions/{code}")
    public PermissionView updatePermissionMetadata(
            @PathVariable String code,
            @Valid @RequestBody UpdatePermissionMetadataRequest body
    ) {
        var updated = permissionService.updateMetadata(code, body.descriptionFa(), body.category());
        return new PermissionView(updated.getCode(), updated.getDescriptionFa(), updated.getCategory());
    }

    // ============================ ROLES: READ ============================

    @Operation(summary = "لیست تمام نقش‌ها")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "لیست نقش‌ها با دسترسی‌هایشان"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی ROLE_WRITE ندارید")
    })
    @GetMapping("/roles")
    public List<RoleView> listRoles() {
        return adminService.listRoles();
    }

    @Operation(summary = "دریافت جزئیات یک نقش با شناسه")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "جزئیات نقش"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی ROLE_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "نقش یافت نشد")
    })
    @GetMapping("/roles/{id}")
    public RoleView getRole(@PathVariable Long id) {
        return adminService.getRole(id);
    }

    // ============================ ROLES: CREATE / UPDATE / DELETE ============================

    @Operation(
            summary = "ساخت نقش جدید با دسترسی‌های تعیین‌شده",
            description = """
                    یک نقش جدید (systemRole=false، system-wide) می‌سازد.
                    اسم نقش باید یکتا و با فرمت UPPER_SNAKE_CASE باشد. اسم‌های محفوظ سیستمی
                    (SUPER_ADMIN، COMPANY_ADMIN، ACCOUNTANT، MANAGER، EMPLOYEE، AUDITOR)
                    قابل استفاده نیستند.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "نقش جدید ساخته شد"),
            @ApiResponse(responseCode = "400", description = "اسم نامعتبر یا کد دسترسی نامعتبر"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی ROLE_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "کد دسترسی یافت نشد"),
            @ApiResponse(responseCode = "409", description = "اسم نقش تکراری یا محفوظ سیستمی")
    })
    @PostMapping("/roles")
    public ResponseEntity<RoleView> createRole(
            @Valid @RequestBody CreateRoleRequest body,
            @AuthenticationPrincipal AuthenticatedPrincipal principal
    ) {
        var created = adminService.createRole(body, principal.userId(), principal.username());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(
            summary = "ویرایش نام یا توضیح یک نقش",
            description = """
                    فقط نام و توضیح قابل ویرایش هستند. برای تغییر دسترسی‌ها از endpoint
                    PUT /roles/{id}/permissions استفاده کنید. نقش‌های سیستمی قابل ویرایش نیستند.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "نقش به‌روزرسانی شد"),
            @ApiResponse(responseCode = "400", description = "نقش سیستمی قابل ویرایش نیست، یا نام نامعتبر"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی ROLE_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "نقش یافت نشد"),
            @ApiResponse(responseCode = "409", description = "نام نقش تکراری یا محفوظ سیستمی")
    })
    @PutMapping("/roles/{id}")
    public RoleView updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest body,
            @AuthenticationPrincipal AuthenticatedPrincipal principal
    ) {
        return adminService.updateRole(id, body, principal.userId(), principal.username());
    }

    @Operation(
            summary = "حذف یک نقش",
            description = """
                    نقش‌های سیستمی قابل حذف نیستند. اگر کاربری این نقش را داشته باشد،
                    حذف مسدود می‌شود (خطای 409). ابتدا کاربران را از این نقش خارج کنید.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "نقش حذف شد"),
            @ApiResponse(responseCode = "400", description = "نقش سیستمی قابل حذف نیست"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی ROLE_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "نقش یافت نشد"),
            @ApiResponse(responseCode = "409", description = "کاربرانی این نقش را دارند")
    })
    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> deleteRole(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedPrincipal principal
    ) {
        adminService.deleteRole(id, principal.userId(), principal.username());
        return ResponseEntity.noContent().build();
    }

    // ============================ ROLE PERMISSIONS ============================

    @Operation(
            summary = "به‌روزرسانی کامل دسترسی‌های یک نقش",
            description = """
                    لیست دسترسی‌های نقش را به‌طور کامل جایگزین می‌کند (replace کامل، نه merge).
                    
                    ⚠️ **مهم**: تغییرات فقط پس از login بعدی کاربران این نقش اعمال می‌شود
                    (چون دسترسی‌ها در JWT کش می‌شوند تا انقضا).
                    
                    محدودیت: دسترسی ROLE_WRITE را نمی‌توان از نقش SUPER_ADMIN حذف کرد
                    (جلوگیری از قفل‌شدن سیستم).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "دسترسی‌های نقش به‌روزرسانی شد"),
            @ApiResponse(responseCode = "400", description = "کد دسترسی نامعتبر یا حذف ROLE_WRITE از SUPER_ADMIN"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی ROLE_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "نقش یا کد دسترسی یافت نشد")
    })
    @PutMapping("/roles/{id}/permissions")
    public RoleView updateRolePermissions(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRolePermissionsRequest body,
            @AuthenticationPrincipal AuthenticatedPrincipal principal
    ) {
        return adminService.updatePermissions(
                id,
                body.permissionCodes(),
                principal.userId(),
                principal.username()
        );
    }
}
