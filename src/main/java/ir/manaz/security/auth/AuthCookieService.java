package ir.manaz.security.auth;

import ir.manaz.config.AppSecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Issues / clears httpOnly auth cookies. Tokens also remain in the JSON body
 * for non-browser clients; the SPA prefers cookies and ignores the body tokens.
 */
@Component
@RequiredArgsConstructor
public class AuthCookieService {

    private final AppSecurityProperties props;

    public void writeAuthCookies(HttpServletResponse response,
                                 String accessToken,
                                 String refreshToken,
                                 boolean rememberMe) {
        var cookie = props.getCookie();
        if (!cookie.isEnabled()) return;

        long accessMaxAge = props.getJwt().getAccessTokenExpiration() / 1000;
        long refreshMaxAge = rememberMe
                ? props.getJwt().getRefreshTokenExpiration() / 1000
                : -1; // session cookie when "remember me" is off

        response.addHeader(HttpHeaders.SET_COOKIE, build(
                cookie.getAccessName(), accessToken, cookie.getAccessPath(), accessMaxAge).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, build(
                cookie.getRefreshName(), refreshToken, cookie.getRefreshPath(), refreshMaxAge).toString());
    }

    public void clearAuthCookies(HttpServletResponse response) {
        var cookie = props.getCookie();
        if (!cookie.isEnabled()) return;

        response.addHeader(HttpHeaders.SET_COOKIE, clear(cookie.getAccessName(), cookie.getAccessPath()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clear(cookie.getRefreshName(), cookie.getRefreshPath()).toString());
    }

    public String readAccessToken(HttpServletRequest request) {
        return read(request, props.getCookie().getAccessName());
    }

    public String readRefreshToken(HttpServletRequest request) {
        return read(request, props.getCookie().getRefreshName());
    }

    private ResponseCookie build(String name, String value, String path, long maxAgeSeconds) {
        var cookie = props.getCookie();
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookie.isSecure())
                .path(path)
                .sameSite(cookie.getSameSite());

        if (StringUtils.hasText(cookie.getDomain())) {
            builder.domain(cookie.getDomain());
        }
        if (maxAgeSeconds >= 0) {
            builder.maxAge(Duration.ofSeconds(maxAgeSeconds));
        }
        return builder.build();
    }

    private ResponseCookie clear(String name, String path) {
        var cookie = props.getCookie();
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookie.isSecure())
                .path(path)
                .maxAge(Duration.ZERO)
                .sameSite(cookie.getSameSite());
        if (StringUtils.hasText(cookie.getDomain())) {
            builder.domain(cookie.getDomain());
        }
        return builder.build();
    }

    private static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && StringUtils.hasText(c.getValue())) {
                return c.getValue();
            }
        }
        return null;
    }
}
