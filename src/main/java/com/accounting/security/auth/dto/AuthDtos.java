package com.accounting.security.auth.dto;

import com.accounting.security.auth.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * All auth DTOs kept together as records for a compact base module.
 */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank String usernameOrEmail,
            @NotBlank String password,
            /** Optional - tenant code (e.g. from subdomain). If null, resolved from user. */
            String tenantCode
    ) {}

    public record LoginResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserInfo user
    ) {}

    public record UserInfo(
            Long id,
            Long tenantId,
            String username,
            String email,
            String firstName,
            String lastName,
            java.util.Set<String> roles,
            java.util.Set<String> permissions
    ) {}

    public record RegisterRequest(
            @NotBlank @Size(max = 50) String tenantCode,
            @NotBlank @Size(min = 3, max = 100) String username,
            @NotBlank @Email @Size(max = 150) String email,
            @ValidPassword String password,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName
    ) {}

    public record RefreshTokenRequest(
            @NotBlank String refreshToken
    ) {}

    public record ForgotPasswordRequest(
            @NotBlank @Email String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @ValidPassword String newPassword
    ) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @ValidPassword String newPassword
    ) {}

    public record LogoutRequest(
            @NotBlank String refreshToken
    ) {}
}
