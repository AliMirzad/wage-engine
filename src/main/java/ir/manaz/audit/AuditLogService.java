package ir.manaz.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;

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
                    .createdAt(Instant.now())
                    .build();
            repository.save(logEntry);
        } catch (Exception ex) {
            log.warn("Failed to write audit log entry: {}", ex.getMessage());
        }
    }

    public void log(String event, AuditOutcome outcome, Long tenantId, Long userId, String username, String details) {
        log(event, outcome, tenantId, userId, username, null, null, details);
    }

    private String extractIp(HttpServletRequest req) {
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) return h.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
