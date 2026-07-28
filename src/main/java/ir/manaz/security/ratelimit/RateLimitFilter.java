package ir.manaz.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.manaz.common.ErrorResponse;
import ir.manaz.security.ratelimit.IpRateLimiter.RateLimitCategory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * فیلتر rate limit روی endpointهای حساس auth. قبل از {@code JwtAuthenticationFilter}
 * اجرا می‌شود چون شکست باید بدون درگیر شدن با auth انجام شود.
 * <p>
 * IP از {@code X-Forwarded-For} استخراج می‌شود اگر تنظیم شده باشد، وگرنه
 * از {@code request.getRemoteAddr()} — همان قرارداد {@link ir.manaz.audit.AuditLogService}.
 * فرض بر این است که application روی reverse proxy مطمئن قرار دارد. اگر مستقیم روی
 * اینترنت است، proxy را قبل از هرچیز اضافه کنید تا مهاجم نتواند با header جعلی
 * IP خودش را عوض کند.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** endpointهای حساس + دسته آن. GET/OPTIONS و مسیرهای غیر لیست‌شده اثری نمی‌گیرند. */
    private static final Map<String, RateLimitCategory> LIMITED = Map.of(
            "/api/v1/auth/login", RateLimitCategory.AUTH,
            "/api/v1/auth/refresh", RateLimitCategory.AUTH,
            "/api/v1/auth/reset-password", RateLimitCategory.AUTH,
            "/api/v1/auth/forgot-password", RateLimitCategory.FORGOT_PASSWORD
    );

    private final IpRateLimiter limiter;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        RateLimitCategory category = matchCategory(request.getRequestURI());
        if (category == null) {
            chain.doFilter(request, response);
            return;
        }

        String ip = extractIp(request);
        if (!limiter.tryAcquire(ip, category)) {
            log.warn("Rate limit hit ip={} path={} category={}", ip, request.getRequestURI(), category);
            writeTooMany(response, request.getRequestURI());
            return;
        }
        chain.doFilter(request, response);
    }

    private RateLimitCategory matchCategory(String path) {
        for (var entry : LIMITED.entrySet()) {
            if (MATCHER.match(entry.getKey(), path)) return entry.getValue();
        }
        return null;
    }

    private String extractIp(HttpServletRequest req) {
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) return h.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private void writeTooMany(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", "60");
        ErrorResponse body = ErrorResponse.of(
                429, "Too Many Requests", "error.rate_limited",
                "تعداد درخواست‌ها بیش از حد مجاز است. لطفاً کمی صبر کنید.",
                path);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
