package ir.manaz.security.permission.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.manaz.security.jwt.AuthenticatedPrincipal;
import ir.manaz.security.permission.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static ir.manaz.security.permission.admin.AdminRolePermissionDtos.*;

/**
 * Admin endpoints for managing roles and their permissions.
 *
 * <p>All endpoints require {@code ROLE_WRITE} authority (currently only {@code SUPER_ADMIN}
 * has it by default; admin can extend to {@code COMPANY_ADMIN} if desired).
 *
 * <p><b>Note on caching:</b> permission changes take effect only after the affected user
 * logs in again or refreshes their access token (JWT contains permissions at issue time).
 * The UI should warn admins about this.
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

    // -------- Permission catalog --------

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
            description = "کد دسترسی قابل تغییر نیست؛ فقط متادیتای نمایشی. برای اعمال UI فارسی سفارشی."
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

    // -------- Roles --------

    @Operation(
            summary = "لیست تمام نقش‌ها",
            description = "شامل نقش‌های سیستمی (tenantId=null) و در آینده نقش‌های اختصاصی هر شرکت."
    )
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

    @Operation(
            summary = "به‌روزرسانی کامل دسترسی‌های یک نقش",
            description = """
                    لیست دسترسی‌های نقش را به‌طور کامل جایگزین می‌کند (replace کامل، نه merge).
                    
                    ⚠️ **مهم**: تغییرات فقط پس از login بعدی کاربران این نقش اعمال می‌شود
                    (چون دسترسی‌ها در JWT کش می‌شوند تا انقضا). برای اعمال فوری،
                    باید refresh token های کاربران را revoke کنید.
                    
                    محدودیت: دسترسی ROLE_WRITE را نمی‌توان از نقش SUPER_ADMIN حذف کرد
                    (جلوگیری از قفل‌شدن سیستم).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "دسترسی‌های نقش به‌روزرسانی شد"),
            @ApiResponse(responseCode = "400", description = "کد دسترسی نامعتبر، یا حذف ROLE_WRITE از SUPER_ADMIN"),
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