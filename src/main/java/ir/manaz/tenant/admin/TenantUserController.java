package ir.manaz.tenant.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.manaz.common.PageResponse;
import ir.manaz.security.jwt.AuthenticatedPrincipal;
import ir.manaz.security.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static ir.manaz.security.user.UserDtos.*;

@Tag(name = "Admin - Tenant Users",
        description = "مدیریت کاربران یک شرکت توسط SUPER_ADMIN — مسیر نجات وقتی شرکت مدیر فعالی ندارد")
@RestController
@RequestMapping("/api/v1/admin/tenants/{tenantId}/users")
@RequiredArgsConstructor
public class TenantUserController {

    private final UserService userService;
    private final TenantService tenantService;

    @Operation(
            summary = "لیست کاربران یک شرکت",
            description = "برخلاف /api/v1/users، شناسه شرکت از مسیر خوانده می‌شود نه از توکن."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "لیست کاربران شرکت"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی TENANT_READ ندارید"),
            @ApiResponse(responseCode = "404", description = "شرکت یافت نشد")
    })
    @PreAuthorize("hasAuthority('TENANT_READ')")
    @GetMapping
    public PageResponse<UserResponse> list(
            @PathVariable Long tenantId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        tenantService.getById(tenantId);   // 404 اگر شرکت وجود نداشته باشد
        return userService.listForTenant(tenantId, pageable);
    }

    @Operation(
            summary = "ساخت کاربر در یک شرکت",
            description = """
                    کاربر جدید در شرکت مشخص‌شده ساخته می‌شود.
                    برخلاف مسیر /api/v1/users، نقش COMPANY_ADMIN نیز قابل تخصیص است —
                    برای زمانی که شرکت مدیر فعالی ندارد. فقط SUPER_ADMIN قابل تخصیص نیست.
                    رمز اولیه فقط یک بار در پاسخ برگردانده می‌شود.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "کاربر ساخته شد؛ رمز اولیه در پاسخ است"),
            @ApiResponse(responseCode = "400", description = "داده نامعتبر یا نقش غیرقابل تخصیص"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی TENANT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "شرکت یا نقش یافت نشد"),
            @ApiResponse(responseCode = "409", description = "نام کاربری یا ایمیل تکراری است")
    })
    @PreAuthorize("hasAuthority('TENANT_WRITE')")
    @PostMapping
    public ResponseEntity<CreateUserResponse> create(
            @PathVariable Long tenantId,
            @Valid @RequestBody CreateUserRequest req,
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        tenantService.getById(tenantId);
        return ResponseEntity.status(201).body(
                userService.createForTenant(tenantId, req, principal.userId(), principal.username()));
    }

    @Operation(
            summary = "بازنشانی رمز کاربر یک شرکت",
            description = """
                    رمز جدید تولید و فقط یک بار برگردانده می‌شود. حساب فعال و قفلش پاک می‌شود
                    و همه‌ی نشست‌هایش باطل. برای زمانی که مدیر شرکت رمزش را گم کرده
                    و به ایمیل بازیابی دسترسی ندارد.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "رمز جدید تولید شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی TENANT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "شرکت یا کاربر یافت نشد")
    })
    @PreAuthorize("hasAuthority('TENANT_WRITE')")
    @PostMapping("/{userId}/reset-password")
    public Map<String, String> resetPassword(@PathVariable Long tenantId,
                                             @PathVariable Long userId,
                                             @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return Map.of("initialPassword",
                userService.resetPasswordForTenant(tenantId, userId,
                        principal.userId(), principal.username()));
    }

    @Operation(
            summary = "ارتقای کاربر به مدیر شرکت",
            description = """
                    نقش COMPANY_ADMIN را به کاربر موجود اضافه می‌کند (نقش‌های قبلی حفظ می‌شوند)
                    و حسابش را فعال می‌کند. برای زمانی که مدیر قبلی شرکت را ترک کرده است.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "کاربر به مدیر شرکت ارتقا یافت"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی TENANT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "شرکت یا کاربر یافت نشد"),
            @ApiResponse(responseCode = "409", description = "کاربر از قبل مدیر شرکت است")
    })
    @PreAuthorize("hasAuthority('TENANT_WRITE')")
    @PostMapping("/{userId}/grant-company-admin")
    public UserResponse grantCompanyAdmin(@PathVariable Long tenantId,
                                          @PathVariable Long userId,
                                          @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return userService.grantCompanyAdmin(tenantId, userId,
                principal.userId(), principal.username());
    }
}