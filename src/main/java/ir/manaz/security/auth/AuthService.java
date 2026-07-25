package ir.manaz.security.auth;

import ir.manaz.audit.AuditEvent;
import ir.manaz.audit.AuditLogService;
import ir.manaz.audit.AuditOutcome;
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
import ir.manaz.security.token.PasswordResetToken;
import ir.manaz.security.token.PasswordResetTokenRepository;
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
import ir.manaz.exception.ConflictException;
import ir.manaz.exception.UnauthorizedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashSet;
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
    private final PasswordResetTokenRepository resetTokenRepository;
    private final AppSecurityProperties props;

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

        loginAttemptService.loginSucceeded(key);

        CustomUserDetails principal = new CustomUserDetails(user);
        return issueTokens(principal, user, httpReq, AuditEvent.LOGIN);
    }

    // ------------------------------- REGISTER -------------------------------
    @Transactional
    public LoginResponse register(RegisterRequest req, HttpServletRequest httpReq) {
        Tenant tenant = tenantRepository.findByCode(req.tenantCode())
                .orElseThrow(() -> new NotFoundException("tenant.not_found", req.tenantCode()));

        String username = req.username().trim().toLowerCase();
        String email    = req.email().trim().toLowerCase();

        if (userRepository.existsByUsernameIgnoreCase(username))
            throw new ConflictException("user.username.duplicate");
        if (userRepository.existsByEmailIgnoreCase(email))
            throw new ConflictException("user.email.duplicate");

        Role employeeRole = roleRepository.findByNameAndTenantIdIsNull(DefaultRoles.EMPLOYEE)
                .orElseThrow(() -> new IllegalStateException("system.role.default_missing"));

        Set<Role> roles = new HashSet<>();
        roles.add(employeeRole);

        User user = User.builder()
                .tenantId(tenant.getId())
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(req.password()))
                .firstName(req.firstName())
                .lastName(req.lastName())
                .enabled(true)
                .accountNonLocked(true)
                .passwordChangedAt(Instant.now())
                .roles(roles)
                .build();
        user = userRepository.save(user);

        auditLogService.log(AuditEvent.REGISTER, AuditOutcome.SUCCESS,
                tenant.getId(), user.getId(), user.getUsername(), "New user registered");

        return issueTokens(new CustomUserDetails(user), user, httpReq, null);
    }

    // ------------------------------- REFRESH -------------------------------
    @Transactional
    public LoginResponse refresh(RefreshTokenRequest req, HttpServletRequest httpReq) {
        Claims claims;
        try {
            claims = jwtService.parse(req.refreshToken());
        } catch (Exception ex) {
            throw new UnauthorizedException("auth.refresh_token.invalid");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new UnauthorizedException("auth.refresh_token.wrong_type");
        }

        String jti = claims.getId();
        RefreshToken stored = refreshTokenService.getActive(jti);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new NotFoundException("user.not_found"));

        refreshTokenService.revoke(jti);

        CustomUserDetails principal = new CustomUserDetails(user);
        return issueTokens(principal, user, httpReq, AuditEvent.TOKEN_REFRESH);
    }

    // ------------------------------- LOGOUT -------------------------------
    @Transactional
    public void logout(LogoutRequest req, Long userId, String username, Long tenantId) {
        try {
            Claims claims = jwtService.parse(req.refreshToken());
            refreshTokenService.revoke(claims.getId());
        } catch (Exception ignored) { /* silently succeed */ }
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
            throw new BusinessException("auth.password.current.wrong");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(user.getId());

        auditLogService.log(AuditEvent.PASSWORD_CHANGE, AuditOutcome.SUCCESS,
                user.getTenantId(), user.getId(), user.getUsername(), null);
    }

    // ------------------------------- FORGOT PASSWORD -------------------------------
    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        // Always respond identically to avoid email enumeration
        userRepository.findByEmailIgnoreCase(req.email()).ifPresent(user -> {
            String rawToken = generateRawToken();
            String hash = sha256(rawToken);

            PasswordResetToken token = PasswordResetToken.builder()
                    .userId(user.getId())
                    .tokenHash(hash)
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plus(
                            props.getPasswordReset().getTokenExpirationMinutes(), ChronoUnit.MINUTES))
                    .used(false)
                    .build();
            resetTokenRepository.save(token);

            // TODO: hook up email service. For now, log the reset link so devs can test.
            log.info("[PASSWORD RESET] user={} token={} (valid {} min)",
                    user.getEmail(), rawToken, props.getPasswordReset().getTokenExpirationMinutes());

            auditLogService.log(AuditEvent.PASSWORD_RESET_REQUEST, AuditOutcome.SUCCESS,
                    user.getTenantId(), user.getId(), user.getUsername(), null);
        });
    }

    // ------------------------------- RESET PASSWORD -------------------------------
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String hash = sha256(req.token());
        PasswordResetToken token = resetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("auth.reset_token.invalid"));
        if (!token.isValid()) {
            throw new UnauthorizedException("auth.reset_token.invalid");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new NotFoundException("user.not_found"));

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        user.setFailedLoginAttempts(0);
        user.setAccountNonLocked(true);
        user.setLockedUntil(null);
        userRepository.save(user);

        token.setUsed(true);
        token.setUsedAt(Instant.now());
        resetTokenRepository.save(token);

        refreshTokenService.revokeAllForUser(user.getId());

        auditLogService.log(AuditEvent.PASSWORD_RESET_COMPLETE, AuditOutcome.SUCCESS,
                user.getTenantId(), user.getId(), user.getUsername(), null);
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

        Set<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        Set<String> perms = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Enum::name)
                .collect(Collectors.toSet());

        UserInfo info = new UserInfo(
                user.getId(), user.getTenantId(), user.getUsername(), user.getEmail(),
                user.getFirstName(), user.getLastName(), roles, perms
        );
        return new LoginResponse(access, refresh, "Bearer",
                props.getJwt().getAccessTokenExpiration() / 1000, info);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String extractIp(HttpServletRequest req) {
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) return h.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
