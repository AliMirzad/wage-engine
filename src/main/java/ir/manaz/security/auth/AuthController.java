package ir.manaz.security.auth;

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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest httpReq) {
        return ResponseEntity.ok(authService.login(req, httpReq));
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest req,
                                                  HttpServletRequest httpReq) {
        return ResponseEntity.ok(authService.register(req, httpReq));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest req,
                                                 HttpServletRequest httpReq) {
        return ResponseEntity.ok(authService.refresh(req, httpReq));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest req,
                                       @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        Long userId = principal == null ? null : principal.userId();
        String username = principal == null ? null : principal.username();
        Long tenantId = principal == null ? null : principal.tenantId();
        authService.logout(req, userId, username, tenantId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                               @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        authService.changePassword(req, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        authService.forgotPassword(req);
        // Always 204 — do not leak existence of email
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.noContent().build();
    }
}
