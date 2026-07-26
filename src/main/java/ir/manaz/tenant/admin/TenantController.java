package ir.manaz.tenant.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.manaz.common.PageResponse;
import ir.manaz.security.jwt.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static ir.manaz.tenant.admin.TenantDtos.*;

@Tag(name = "Admin - Tenants", description = "مدیریت شرکت‌ها — فقط برای SUPER_ADMIN")
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @Operation(
            summary = "لیست شرکت‌ها",
            description = "همه‌ی شرکت‌های ثبت‌شده را با صفحه‌بندی برمی‌گرداند، مرتب بر اساس تاریخ ثبت (نزولی)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "لیست شرکت‌ها"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی TENANT_READ ندارید")
    })
    @PreAuthorize("hasAuthority('TENANT_READ')")
    @GetMapping
    public PageResponse<TenantResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return tenantService.list(pageable);
    }

    @Operation(summary = "جزئیات یک شرکت")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "اطلاعات شرکت"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی TENANT_READ ندارید"),
            @ApiResponse(responseCode = "404", description = "شرکت یافت نشد")
    })
    @PreAuthorize("hasAuthority('TENANT_READ')")
    @GetMapping("/{id}")
    public TenantResponse getById(@PathVariable Long id) {
        return tenantService.getById(id);
    }

    @Operation(
            summary = "ثبت شرکت جدید همراه با مدیر آن",
            description = """
                    شرکت و اولین کاربر مدیر (نقش COMPANY_ADMIN) را در یک تراکنش می‌سازد.
                    اگر ساخت کاربر شکست بخورد، شرکت هم ثبت نمی‌شود.
                    نام کاربری و ایمیل مدیر در کل سامانه یکتا هستند (نه فقط داخل شرکت).
                    کد شرکت پس از ثبت قابل تغییر نیست.
                    رمز اولیه توسط ثبت‌کننده تعیین می‌شود؛ مدیر شرکت باید در اولین ورود آن را عوض کند.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "شرکت و مدیر آن ثبت شدند"),
            @ApiResponse(responseCode = "400", description = "داده نامعتبر (رمز policy را رعایت نمی‌کند، شبا نامعتبر و …)"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی TENANT_WRITE ندارید"),
            @ApiResponse(responseCode = "409", description = "کد شرکت یا نام کاربری/ایمیل مدیر تکراری است")
    })
    @PreAuthorize("hasAuthority('TENANT_WRITE')")
    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest req,
                                                 @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        TenantResponse created = tenantService.create(req, principal.userId(), principal.username());
        return ResponseEntity.status(201).body(created);
    }

    @Operation(
            summary = "ویرایش اطلاعات شرکت",
            description = "فقط فیلدهای ارسال‌شده به‌روز می‌شوند. کد شرکت غیرقابل تغییر است."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "اطلاعات شرکت به‌روز شد"),
            @ApiResponse(responseCode = "400", description = "داده نامعتبر"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی TENANT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "شرکت یافت نشد")
    })
    @PreAuthorize("hasAuthority('TENANT_WRITE')")
    @PutMapping("/{id}")
    public TenantResponse update(@PathVariable Long id,
                                 @Valid @RequestBody UpdateTenantRequest req,
                                 @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return tenantService.update(id, req, principal.userId(), principal.username());
    }

    @Operation(
            summary = "غیرفعال کردن شرکت",
            description = """
                    شرکت را غیرفعال می‌کند. همه‌ی کاربران آن شرکت بلافاصله از ورود محروم می‌شوند
                    (پیام auth.tenant_inactive). نشست‌های فعال تا انقضای access token (۱۵ دقیقه)
                    کار می‌کنند ولی قابل تمدید نیستند. داده‌ها حذف نمی‌شوند.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "شرکت غیرفعال شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی TENANT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "شرکت یافت نشد"),
            @ApiResponse(responseCode = "409", description = "شرکت از قبل غیرفعال است")
    })
    @PreAuthorize("hasAuthority('TENANT_WRITE')")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id,
                                           @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        tenantService.deactivate(id, principal.userId(), principal.username());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "فعال کردن مجدد شرکت")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "شرکت فعال شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی TENANT_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "شرکت یافت نشد"),
            @ApiResponse(responseCode = "409", description = "شرکت از قبل فعال است")
    })
    @PreAuthorize("hasAuthority('TENANT_WRITE')")
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id,
                                         @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        tenantService.activate(id, principal.userId(), principal.username());
        return ResponseEntity.noContent().build();
    }
}