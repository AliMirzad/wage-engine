package ir.manaz.security.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import ir.manaz.security.auth.dto.AuthDtos;
import ir.manaz.security.auth.dto.AuthDtos.*;
import ir.manaz.security.jwt.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Login با username/email + password",
            description = "برمی‌گرداند: accessToken (15m), refreshToken (7d)، پروفایل کاربر با permissionهای resolve شده.",
            security = {}  // public endpoint — override می‌کنه global security رو
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "احراز هویت موفق"),
            @ApiResponse(responseCode = "401", description = "نام کاربری/رمز اشتباه (code=auth.invalid_credentials) یا حساب قفل‌شده (code=auth.account_locked)")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest httpReq) {
        return ResponseEntity.ok(authService.login(req, httpReq));
    }

    @Operation(summary = "تعویض refresh token با access token جدید", security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "توکن‌های جدید صادر شد"),
            @ApiResponse(responseCode = "401", description = "refresh token نامعتبر / منقضی / revoke شده")
    })
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest req,
                                                 HttpServletRequest httpReq) {
        return ResponseEntity.ok(authService.refresh(req, httpReq));
    }

    @Operation(
            summary = "خروج از session فعلی",
            description = "refresh token داده‌شده را revoke می‌کند. access token فعلی تا expiry معتبر می‌ماند."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "خروج انجام شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest req,
                                       @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        Long userId = principal == null ? null : principal.userId();
        String username = principal == null ? null : principal.username();
        Long tenantId = principal == null ? null : principal.tenantId();
        authService.logout(req, userId, username, tenantId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "تغییر رمز کاربر لاگین‌شده",
            description = "پس از تغییر، همه‌ی refresh tokenهای دیگر revoke می‌شوند."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "رمز تغییر کرد"),
            @ApiResponse(responseCode = "400", description = "رمز جدید policy را رعایت نمی‌کند"),
            @ApiResponse(responseCode = "401", description = "رمز فعلی اشتباه یا احراز هویت لازم")
    })
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                               @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        authService.changePassword(req, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "درخواست لینک بازیابی رمز",
            description = "همیشه 200 برمی‌گرداند تا مشخص نشود ایمیل ثبت شده یا نه. در dev، token در لاگ سرور نوشته می‌شود.",
            security = {}
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        // Always 204 — do not leak existence of email
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "تعیین رمز جدید با استفاده از reset token", security = {},
            description = """
                    پس از تغییر رمز، همه‌ی refresh tokenها از جمله نشست فعلی revoke می‌شوند.
                    access token فعلی تا انقضا (۱۵ دقیقه) معتبر می‌ماند ولی قابل تمدید نیست،
                    پس فرانت باید بعد از پاسخ موفق کاربر را logout کرده و به صفحه ورود بفرستد.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "رمز عوض شد؛ همه session ها revoke شدند"),
            @ApiResponse(responseCode = "400", description = "رمز policy را رعایت نمی‌کند"),
            @ApiResponse(responseCode = "401", description = "reset token نامعتبر یا منقضی")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "پروفایل و دسترسی‌های کاربر جاری",
            description = """
                    اطلاعات کاربر احراز‌شده را همراه با نقش‌ها و لیست flat دسترسی‌ها برمی‌گرداند.
                    داده از دیتابیس خوانده می‌شود، نه از claimهای توکن — پس اگر ادمین
                    دسترسی‌های نقش را تغییر داده باشد، نتیجه‌ی این endpoint فوراً به‌روز است
                    (بدون انتظار برای انقضای access token).
                    فرانت باید در هر بار بارگذاری پنل این را صدا بزند و برای رندر منو از آن استفاده کند.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "پروفایل کاربر بازگردانده شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است یا حساب غیرفعال شده")
    })
    @GetMapping("/me")
    public AuthDtos.UserInfo me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return authService.me(principal.userId());
    }
}
