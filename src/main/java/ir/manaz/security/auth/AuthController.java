package ir.manaz.security.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import ir.manaz.security.auth.dto.AuthDtos;
import ir.manaz.security.auth.dto.AuthDtos.*;
import ir.manaz.security.jwt.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    @Operation(
            summary = "Login با username/email + password",
            description = """
                    برمی‌گرداند: accessToken / refreshToken در JSON (برای کلاینت‌های غیرمرورگر)
                    و هم‌زمان cookieهای httpOnly برای SPA. remember=false → refresh cookie بدون Max-Age.
                    """,
            security = {}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "احراز هویت موفق"),
            @ApiResponse(responseCode = "401", description = "نام کاربری/رمز اشتباه (code=auth.invalid_credentials) یا حساب قفل‌شده (code=auth.account_locked)")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest httpReq,
                                               HttpServletResponse httpRes) {
        LoginResponse body = authService.login(req, httpReq);
        authCookieService.writeAuthCookies(
                httpRes, body.accessToken(), body.refreshToken(), req.rememberMe());
        return ResponseEntity.ok(body);
    }

    @Operation(
            summary = "تعویض refresh token با access token جدید",
            security = {},
            description = "refresh را از body یا از cookie می‌خواند و cookieهای تازه می‌نویسد."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "توکن‌های جدید صادر شد"),
            @ApiResponse(responseCode = "401", description = "refresh token نامعتبر / منقضی / revoke شده")
    })
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest req,
            HttpServletRequest httpReq,
            HttpServletResponse httpRes) {
        String refresh = resolveRefresh(req == null ? null : req.refreshToken(), httpReq);
        LoginResponse body = authService.refresh(refresh, httpReq);
        // Keep existing Max-Age semantics: if the browser sent a session refresh cookie,
        // omit Max-Age again (remember=false). Presence of Max-Age on the inbound cookie
        // is not visible here — default to remember=true for rotated cookies.
        authCookieService.writeAuthCookies(httpRes, body.accessToken(), body.refreshToken(), true);
        return ResponseEntity.ok(body);
    }

    @Operation(
            summary = "خروج از session فعلی",
            description = "refresh token (body یا cookie) را revoke و cookieها را پاک می‌کند."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "خروج انجام شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) LogoutRequest req,
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest httpReq,
            HttpServletResponse httpRes) {
        String refresh = resolveRefresh(req == null ? null : req.refreshToken(), httpReq);
        authService.logout(refresh, principal.userId(), principal.username(), principal.tenantId());
        authCookieService.clearAuthCookies(httpRes);
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
                                               @AuthenticationPrincipal AuthenticatedPrincipal principal,
                                               HttpServletResponse httpRes) {
        authService.changePassword(req, principal.userId());
        authCookieService.clearAuthCookies(httpRes);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "درخواست کد بازیابی رمز (OTP)",
            description = """
                    کد ۶ رقمی به ایمیل کاربر ارسال می‌شود. همیشه 204 برمی‌گرداند تا مشخص
                    نشود ایمیل ثبت شده یا نه (email enumeration). در dev، کد در لاگ سرور
                    ثبت می‌شود. reset فقط برای کاربرانی که ایمیلشان تأیید شده کار می‌کند.
                    """,
            security = {}
    )
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "تعیین رمز جدید با کد OTP", security = {},
            description = """
                    کاربر با ایمیل + کد ۶ رقمی + رمز جدید. کد ۱۰ دقیقه معتبر است و حداکثر
                    ۵ تلاش غلط قبل از invalidate. پس از موفقیت، همه‌ی sessionها revoke می‌شوند —
                    فرانت باید کاربر را به صفحه ورود بفرستد.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "رمز عوض شد؛ همه session ها revoke شدند"),
            @ApiResponse(responseCode = "400", description = "رمز policy را رعایت نمی‌کند"),
            @ApiResponse(responseCode = "401", description = "کد OTP نامعتبر / منقضی / تلاش‌های مجاز تمام شده")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req,
                                              HttpServletResponse httpRes) {
        authService.resetPassword(req);
        authCookieService.clearAuthCookies(httpRes);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "تأیید ایمیل کاربر جاری با کد OTP",
            description = """
                    کاربر باید ایمیل خود را با کد ۶ رقمی تأیید کند. تا قبل از تأیید،
                    reset-password کار نمی‌کند. اگر ایمیل قبلاً تأیید شده، 204 برمی‌گرداند
                    (idempotent) — دفعه‌ی دوم submit خطا نمی‌گیرد.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "ایمیل تأیید شد"),
            @ApiResponse(responseCode = "400", description = "فرمت کد نامعتبر"),
            @ApiResponse(responseCode = "401", description = "کد OTP نامعتبر / منقضی / تلاش‌های مجاز تمام شده")
    })
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest req,
                                            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        authService.verifyEmail(principal.userId(), req.code());
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "ارسال مجدد کد تأیید ایمیل",
            description = """
                    یک کد OTP جدید تأیید ایمیل تولید و برای کاربر جاری ارسال می‌کند.
                    اگر ایمیل قبلاً تأیید شده باشد، هیچ ایمیلی فرستاده نمی‌شود ولی پاسخ
                    204 است تا endpoint برای spam قابل سواستفاده نباشد. rate limit مثل
                    forgot-password (۵ در دقیقه از هر IP) اعمال می‌شود.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "درخواست ثبت شد"),
            @ApiResponse(responseCode = "401", description = "احراز هویت لازم است"),
            @ApiResponse(responseCode = "429", description = "درخواست‌های زیاد — کمی صبر کنید")
    })
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        authService.resendEmailVerification(principal.userId());
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

    private String resolveRefresh(String fromBody, HttpServletRequest httpReq) {
        if (StringUtils.hasText(fromBody)) return fromBody.trim();
        String fromCookie = authCookieService.readRefreshToken(httpReq);
        if (StringUtils.hasText(fromCookie)) return fromCookie;
        return null;
    }
}
