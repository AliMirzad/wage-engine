package ir.manaz.security.auth;

import ir.manaz.audit.AuditEvent;
import ir.manaz.audit.AuditLogService;
import ir.manaz.audit.AuditOutcome;
import ir.manaz.email.EmailService;
import ir.manaz.security.auth.dto.AuthDtos.*;
import ir.manaz.config.AppSecurityProperties;
import ir.manaz.exception.BusinessException;
import ir.manaz.security.jwt.JwtService;
import ir.manaz.security.loginattempt.LoginAttemptService;
import ir.manaz.security.role.DefaultRoles;
import ir.manaz.security.role.Role;
import ir.manaz.security.role.RoleRepository;
import ir.manaz.tenant.Tenant;
import ir.manaz.tenant.TenantRepository;
import ir.manaz.security.otp.OtpPurpose;
import ir.manaz.security.otp.OtpService;
import ir.manaz.security.token.RefreshToken;
import ir.manaz.security.token.RefreshTokenService;
import ir.manaz.security.user.CustomUserDetails;
import ir.manaz.security.user.User;
import ir.manaz.security.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ir.manaz.exception.NotFoundException;
import ir.manaz.exception.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final AuditLogService auditLogService;
    private final OtpService otpService;
    private final AppSecurityProperties props;
    private final EmailService emailService;

    // مدت اعتبار OTP بازیابی رمز / تأیید ایمیل — با {@link OtpService} هماهنگ نگه دار.
    private static final int OTP_VALID_MINUTES = 10;

    // ------------------------------- LOGIN -------------------------------
    @Transactional
    public LoginResponse login(LoginRequest req, HttpServletRequest httpReq) {
        String key = req.usernameOrEmail() == null ? "" : req.usernameOrEmail().trim().toLowerCase();

        if (loginAttemptService.isLocked(key)) {
            auditLogService.log(AuditEvent.LOGIN, AuditOutcome.DENIED, null, null, key, "Account locked");
            throw new UnauthorizedException("auth.account_locked");
        }

        User user = userRepository.findByUsernameIgnoreCase(key)
                .or(() -> userRepository.findByEmailIgnoreCase(key))
                .orElse(null);

        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            loginAttemptService.loginFailed(key);
            auditLogService.log(AuditEvent.LOGIN, AuditOutcome.FAILURE, null, null, key, "Bad credentials");
            throw new UnauthorizedException("auth.invalid_credentials");
        }

        if (!user.isEnabled()) {
            auditLogService.log(AuditEvent.LOGIN, AuditOutcome.DENIED,
                    user.getTenantId(), user.getId(), user.getUsername(), "Account disabled");
            throw new UnauthorizedException("auth.account_disabled");
        }

        if (user.getTenantId() != null) {
            boolean tenantActive = tenantRepository.findById(user.getTenantId())
                    .map(Tenant::isActive)
                    .orElse(false);
            if (!tenantActive) {
                auditLogService.log(AuditEvent.LOGIN, AuditOutcome.DENIED,
                        user.getTenantId(), user.getId(), user.getUsername(), "Tenant inactive");
                throw new UnauthorizedException("auth.tenant_inactive");
            }
        }

        loginAttemptService.loginSucceeded(key);

        CustomUserDetails principal = new CustomUserDetails(user);
        return issueTokens(principal, user, httpReq, AuditEvent.LOGIN);
    }

    // ------------------------------- REFRESH -------------------------------
    @Transactional
    public LoginResponse refresh(String refreshToken, HttpServletRequest httpReq) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("auth.refresh_token.invalid");
        }
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (Exception ex) {
            throw new UnauthorizedException("auth.refresh_token.invalid");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new UnauthorizedException("auth.refresh_token.wrong_type");
        }

        String jti = claims.getId();
        // consumeForRotation اتمی است: در صورت race یا replay خطا می‌اندازد
        // و همه sessionها را باطل می‌کند تا مهاجم نتواند از توکن بازنشسته سود ببرد.
        RefreshToken stored = refreshTokenService.consumeForRotation(jti);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new NotFoundException("user.not_found"));

        CustomUserDetails principal = new CustomUserDetails(user);
        return issueTokens(principal, user, httpReq, AuditEvent.TOKEN_REFRESH);
    }

    // ------------------------------- LOGOUT -------------------------------
    @Transactional
    public void logout(String refreshToken, Long userId, String username, Long tenantId) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                Claims claims = jwtService.parse(refreshToken);
                Number tokenUid = claims.get(JwtService.CLAIM_USER_ID, Number.class);
                // فقط توکنی که به همین کاربر تعلق دارد باطل شود — جلوی revoke کردن session دیگران
                if (tokenUid != null && userId != null && tokenUid.longValue() == userId) {
                    refreshTokenService.revoke(claims.getId());
                }
            } catch (Exception ignored) { /* silently succeed */ }
        }
        auditLogService.log(AuditEvent.LOGOUT, AuditOutcome.SUCCESS, tenantId, userId, username, null);
    }

    // ------------------------------- CHANGE PASSWORD -------------------------------
    @Transactional
    public void changePassword(ChangePasswordRequest req, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("user.not_found"));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            auditLogService.log(AuditEvent.PASSWORD_CHANGE, AuditOutcome.FAILURE,
                    user.getTenantId(), user.getId(), user.getUsername(), "Wrong current password");
            throw new UnauthorizedException("auth.password.current.wrong");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(user.getId());

        auditLogService.log(AuditEvent.PASSWORD_CHANGE, AuditOutcome.SUCCESS,
                user.getTenantId(), user.getId(), user.getUsername(), null);
    }

    // ------------------------------- FORGOT PASSWORD -------------------------------
    /**
     * تولید کد OTP بازیابی رمز و ارسال به ایمیل. برای جلوگیری از enumeration،
     * پاسخ همیشه یکسان (204) است — چه کاربر وجود داشته باشد چه نه.
     * <p>
     * الزام تأیید ایمیل: reset-password فقط برای کاربرانی که ایمیلشان تأیید شده
     * قابل استفاده است — جلوی سواستفاده از ایمیل غلطی که SUPER_ADMIN موقع
     * onboarding زده را می‌گیرد. کاربر باید اول ایمیل را تأیید کند.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        userRepository.findByEmailIgnoreCase(email)
                .filter(User::isEnabled)
                .filter(User::isEmailVerified)
                .ifPresent(user -> {
                    String code = otpService.issue(user.getId(), OtpPurpose.PASSWORD_RESET);
                    emailService.sendPasswordResetOtp(user.getEmail(), code, OTP_VALID_MINUTES);
                    auditLogService.log(AuditEvent.PASSWORD_RESET_REQUEST, AuditOutcome.SUCCESS,
                            user.getTenantId(), user.getId(), user.getUsername(), null);
                });
    }

    // ------------------------------- RESET PASSWORD -------------------------------
    /**
     * تعیین رمز جدید با کد OTP. کاربر با ایمیل مشخص می‌شود؛ کد ۶ رقمی از ایمیل و
     * رمز جدید در body می‌آید. در صورت موفقیت همه‌ی sessionها revoke می‌شوند.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String email = req.email() == null ? "" : req.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("otp.invalid"));

        // پیام‌های یکسان برای کاربر disabled/unverified تا مهاجم نتواند بین حالت‌ها تمایز بدهد.
        if (!user.isEnabled() || !user.isEmailVerified()) {
            throw new UnauthorizedException("otp.invalid");
        }

        otpService.verifyAndConsume(user.getId(), OtpPurpose.PASSWORD_RESET, req.code());

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        user.setFailedLoginAttempts(0);
        user.setAccountNonLocked(true);
        user.setLockedUntil(null);
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(user.getId());

        auditLogService.log(AuditEvent.PASSWORD_RESET_COMPLETE, AuditOutcome.SUCCESS,
                user.getTenantId(), user.getId(), user.getUsername(), null);
    }

    // ------------------------------- VERIFY EMAIL -------------------------------
    /**
     * تأیید ایمیل کاربر لاگین‌شده با کد OTP دریافت‌شده در ایمیل.
     * تا قبل از این تأیید، مسیرهای حساس مثل reset-password قابل استفاده نیستند.
     */
    @Transactional
    public void verifyEmail(Long userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("user.not_found"));
        if (user.isEmailVerified()) {
            // idempotent — چون کاربر ممکن است تصادفاً چند بار submit کند
            return;
        }
        otpService.verifyAndConsume(user.getId(), OtpPurpose.EMAIL_VERIFICATION, code);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.save(user);
        auditLogService.log(AuditEvent.EMAIL_VERIFIED, AuditOutcome.SUCCESS,
                user.getTenantId(), user.getId(), user.getUsername(), null);
    }

    /**
     * ارسال مجدد کد تأیید ایمیل. اگر ایمیل قبلاً تأیید شده، کاری نمی‌کند
     * تا endpoint برای فرستادن spam قابل سواستفاده نباشد.
     */
    @Transactional
    public void resendEmailVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("user.not_found"));
        if (user.isEmailVerified()) return;
        String code = otpService.issue(user.getId(), OtpPurpose.EMAIL_VERIFICATION);
        emailService.sendEmailVerificationOtp(user.getEmail(), code, OTP_VALID_MINUTES);
    }

    @Transactional(readOnly = true)
    public UserInfo me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("auth.user_not_found"));
        if (!user.isEnabled()) {
            throw new UnauthorizedException("auth.account_disabled");
        }
        return toUserInfo(user);
    }

    // ------------------------------- HELPERS -------------------------------
    private LoginResponse issueTokens(CustomUserDetails principal, User user,
                                      HttpServletRequest req, String auditEvent) {
        String access = jwtService.generateAccessToken(principal);
        String ip = req == null ? null : extractIp(req);
        String ua = req == null ? null : req.getHeader("User-Agent");

        RefreshToken stored = refreshTokenService.create(principal, ua, ip);
        String refresh = jwtService.generateRefreshToken(principal, stored.getJti());

        if (auditEvent != null) {
            auditLogService.log(auditEvent, AuditOutcome.SUCCESS,
                    user.getTenantId(), user.getId(), user.getUsername(), null);
        }

        UserInfo info = toUserInfo(user);

        return new LoginResponse(access, refresh, "Bearer",
                props.getJwt().getAccessTokenExpiration() / 1000, info);
    }

    private String extractIp(HttpServletRequest req) {
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) return h.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private UserInfo toUserInfo(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        Set<String> perms = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Enum::name)
                .collect(Collectors.toSet());
        return new UserInfo(
                user.getId(), user.getTenantId(), user.getUsername(), user.getEmail(),
                user.isEmailVerified(),
                user.getFirstName(), user.getLastName(), roles, perms
        );
    }
}
