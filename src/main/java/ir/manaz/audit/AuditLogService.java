package ir.manaz.audit;

import ir.manaz.common.SecurityHelper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;
    private final SecurityHelper securityHelper;

    /**
     * ثبت رویداد برای موجودیت مشخص با کاربر جاری از SecurityContext.
     * سرویس‌های payroll باید از این استفاده کنند نه از overloadهای دستی.
     */
    public void logForCurrent(String event, AuditOutcome outcome, Long tenantId,
                              String targetType, Long targetId, String details) {
        log(event, outcome, tenantId,
                securityHelper.currentUserId(), securityHelper.currentUsername(),
                targetType, targetId == null ? null : String.valueOf(targetId), details);
    }

    @Async
    public void log(String event, AuditOutcome outcome, Long tenantId, Long userId,
                    String username, String targetType, String targetId, String details) {
        try {
            String ip = null;
            String ua = null;
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                ip = extractIp(req);
                ua = req.getHeader("User-Agent");
            }

            Instant createdAt = Instant.now();
            AuditLog logEntry = AuditLog.builder()
                    .event(event)
                    .outcome(outcome)
                    .tenantId(tenantId)
                    .userId(userId)
                    .username(username)
                    .targetType(targetType)
                    .targetId(targetId)
                    .details(details)
                    .ipAddress(ip)
                    .userAgent(ua)
                    .createdAt(createdAt)
                    .build();
            logEntry.setRowHash(computeHash(logEntry));
            repository.save(logEntry);
        } catch (Exception ex) {
            log.warn("Failed to write audit log entry: {}", ex.getMessage());
        }
    }

    /**
     * hash محتوای ردیف — برای تشخیص دستکاری. الان chain نیست (prev_hash نداریم)،
     * پس اگر مهاجم trigger append-only را دور بزند، می‌تواند هم مقدار و هم hash
     * را با هم بازنویسی کند. برای integrity رسمی، prev_hash در migration بعدی
     * اضافه شود و مقدارش با SELECT ... FOR UPDATE از آخرین ردیف خوانده شود.
     */
    private byte[] computeHash(AuditLog e) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String canonical = String.join("|",
                    nz(e.getEvent()),
                    e.getOutcome() == null ? "" : e.getOutcome().name(),
                    ns(e.getTenantId()),
                    ns(e.getUserId()),
                    nz(e.getUsername()),
                    nz(e.getTargetType()),
                    nz(e.getTargetId()),
                    nz(e.getDetails()),
                    nz(e.getIpAddress()),
                    e.getCreatedAt() == null ? "" : e.getCreatedAt().toString()
            );
            return md.digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String ns(Long v)   { return v == null ? "" : v.toString(); }

    public void log(String event, AuditOutcome outcome, Long tenantId, Long userId, String username, String details) {
        log(event, outcome, tenantId, userId, username, null, null, details);
    }

    private String extractIp(HttpServletRequest req) {
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) return h.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
