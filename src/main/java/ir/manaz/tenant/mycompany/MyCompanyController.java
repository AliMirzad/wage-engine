package ir.manaz.tenant.mycompany;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.manaz.security.jwt.AuthenticatedPrincipal;
import ir.manaz.tenant.admin.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

        import static ir.manaz.tenant.mycompany.MyCompanyDtos.*;

@Tag(name = "My Company", description = "مشاهده و ویرایش اطلاعات شرکت جاری توسط مدیر همان شرکت")
@RestController
@RequestMapping("/api/v1/my-company")
@RequiredArgsConstructor
public class MyCompanyController {

    private final TenantService tenantService;

    @Operation(
            summary = "اطلاعات شرکت جاری",
            description = """
                    اطلاعات شرکتی را برمی‌گرداند که کاربر جاری به آن تعلق دارد.
                    شناسه شرکت از توکن خوانده می‌شود، نه از ورودی — پس دسترسی به شرکت دیگری ممکن نیست.
                    این اطلاعات در سربرگ فیش حقوقی و فایل بانکی استفاده می‌شوند.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "اطلاعات شرکت"),
            @ApiResponse(responseCode = "400", description = "کاربر به هیچ شرکتی تعلق ندارد (مثلاً SUPER_ADMIN)"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی SETTINGS_READ ندارید"),
            @ApiResponse(responseCode = "404", description = "شرکت یافت نشد")
    })
    @PreAuthorize("hasAuthority('SETTINGS_READ')")
    @GetMapping
    public MyCompanyResponse get() {
        return tenantService.getMyCompany();
    }

    @Operation(
            summary = "ویرایش اطلاعات شرکت جاری",
            description = """
                    فقط فیلدهای ارسال‌شده به‌روز می‌شوند.
                    کد شرکت و وضعیت فعال/غیرفعال از این مسیر قابل تغییر نیستند —
                    آن‌ها فقط توسط SUPER_ADMIN از مسیر /api/v1/admin/tenants قابل مدیریت‌اند.
                    کد کارگاه بیمه، کد اقتصادی و شبا پیش از اولین صدور لیست بیمه یا فایل بانکی باید تکمیل شوند.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "اطلاعات شرکت به‌روز شد"),
            @ApiResponse(responseCode = "400", description = "داده نامعتبر (فرمت شبا اشتباه و …) یا کاربر به شرکتی تعلق ندارد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی SETTINGS_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "شرکت یافت نشد")
    })
    @PreAuthorize("hasAuthority('SETTINGS_WRITE')")
    @PutMapping
    public MyCompanyResponse update(@Valid @RequestBody UpdateMyCompanyRequest req,
                                    @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return tenantService.updateMyCompany(req, principal.userId(), principal.username());
    }
}
