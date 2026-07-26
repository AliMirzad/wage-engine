package ir.manaz.security.user;

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

import java.util.Map;

import static ir.manaz.security.user.UserDtos.*;

@Tag(name = "Users", description = "مدیریت کاربران شرکت — ساخت حسابدار، مدیر و سایر کاربران پنل")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "لیست کاربران شرکت",
            description = "فقط کاربران شرکت جاری را برمی‌گرداند. شناسه شرکت از توکن خوانده می‌شود."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "لیست کاربران"),
            @ApiResponse(responseCode = "400", description = "کاربر به هیچ شرکتی تعلق ندارد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی USER_READ ندارید")
    })
    @PreAuthorize("hasAuthority('USER_READ')")
    @GetMapping
    public PageResponse<UserResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return userService.list(pageable);
    }

    @Operation(summary = "جزئیات یک کاربر")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "اطلاعات کاربر"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی USER_READ ندارید"),
            @ApiResponse(responseCode = "404", description = "کاربر یافت نشد")
    })
    @PreAuthorize("hasAuthority('USER_READ')")
    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @Operation(
            summary = "ساخت کاربر جدید در شرکت جاری",
            description = """
                    کاربر در شرکت کاربر جاری ساخته می‌شود؛ شناسه شرکت از توکن می‌آید نه از body.
                    نام کاربری و ایمیل در کل سامانه یکتا هستند.
                    رمز اولیه توسط سرور تولید و **فقط یک بار** در پاسخ برگردانده می‌شود —
                    در هیچ جای دیگری قابل بازیابی نیست. آن را به کاربر تحویل دهید تا با
                    /api/v1/auth/change-password عوضش کند.
                    نقش‌های سیستمی قابل تخصیص: ACCOUNTANT، MANAGER، EMPLOYEE، AUDITOR.
                    SUPER_ADMIN و COMPANY_ADMIN قابل تخصیص نیستند.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "کاربر ساخته شد؛ رمز اولیه در پاسخ است"),
            @ApiResponse(responseCode = "400", description = "داده نامعتبر یا نقش غیرقابل تخصیص"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی USER_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "نقش یافت نشد"),
            @ApiResponse(responseCode = "409", description = "نام کاربری یا ایمیل تکراری است")
    })
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @PostMapping
    public ResponseEntity<CreateUserResponse> create(@Valid @RequestBody CreateUserRequest req,
                                                     @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return ResponseEntity.status(201)
                .body(userService.create(req, principal.userId(), principal.username()));
    }

    @Operation(
            summary = "ویرایش کاربر",
            description = """
                    نام کاربری غیرقابل تغییر است. اگر roleNames ارسال شود، نقش‌ها کاملاً جایگزین می‌شوند.
                    کاربر نمی‌تواند حساب خودش را از این مسیر ویرایش کند.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "کاربر به‌روز شد"),
            @ApiResponse(responseCode = "400", description = "داده نامعتبر، نقش غیرقابل تخصیص، یا ویرایش حساب خود"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی USER_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "کاربر یا نقش یافت نشد"),
            @ApiResponse(responseCode = "409", description = "ایمیل تکراری است")
    })
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id,
                               @Valid @RequestBody UpdateUserRequest req,
                               @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return userService.update(id, req, principal.userId(), principal.username());
    }

    @Operation(
            summary = "غیرفعال کردن کاربر",
            description = """
                    کاربر دیگر نمی‌تواند وارد شود و همه‌ی refresh tokenهایش باطل می‌شوند.
                    access token فعلی تا ۱۵ دقیقه معتبر می‌ماند ولی قابل تمدید نیست.
                    حذف واقعی وجود ندارد — تاریخچه باید حفظ شود.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "کاربر غیرفعال شد"),
            @ApiResponse(responseCode = "400", description = "نمی‌توانید حساب خود را غیرفعال کنید"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی USER_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "کاربر یافت نشد"),
            @ApiResponse(responseCode = "409", description = "کاربر از قبل غیرفعال است")
    })
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id,
                                           @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        userService.deactivate(id, principal.userId(), principal.username());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "فعال کردن مجدد کاربر",
            description = "قفل ناشی از تلاش‌های ناموفق نیز پاک می‌شود."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "کاربر فعال شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی USER_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "کاربر یافت نشد"),
            @ApiResponse(responseCode = "409", description = "کاربر از قبل فعال است")
    })
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id,
                                         @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        userService.activate(id, principal.userId(), principal.username());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "بازنشانی رمز کاربر توسط مدیر",
            description = """
                    رمز جدید تولید و **فقط یک بار** برگردانده می‌شود. همه‌ی نشست‌های کاربر باطل
                    و قفل احتمالی حساب پاک می‌شود. برای زمانی که کاربر رمزش را فراموش کرده
                    و به ایمیل بازیابی دسترسی ندارد.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "رمز جدید تولید شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "403", description = "دسترسی USER_WRITE ندارید"),
            @ApiResponse(responseCode = "404", description = "کاربر یافت نشد")
    })
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @PostMapping("/{id}/reset-password")
    public Map<String, String> resetPassword(@PathVariable Long id,
                                             @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return Map.of("initialPassword",
                userService.resetPassword(id, principal.userId(), principal.username()));
    }
}