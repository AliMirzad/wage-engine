package ir.manaz.audit;

import ir.manaz.audit.AuditLogDtos.AuditLogResponse;
import ir.manaz.common.PageResponse;
import ir.manaz.exception.BusinessException;
import ir.manaz.tenant.TenantContext;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * جست‌وجو در audit logs. جدا از {@link AuditLogService} است چون آن سرویس
 * فقط برای نوشتن است و write path نباید read logic را با خود بکشد.
 * <p>
 * فیلترها اختیاری‌اند و هر ترکیبی مجاز است. مرتب‌سازی همیشه نزولی روی
 * createdAt — audit به‌طور طبیعی chronological است.
 */
@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final AuditLogRepository repository;

    /**
     * جست‌وجو در audit logs شرکت جاری. tenantId از token خوانده می‌شود؛
     * اگر کاربر جاری به شرکتی تعلق ندارد (مثلاً SUPER_ADMIN)، خطا می‌دهد
     * چون این مسیر برای platform-wide نیست.
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> searchForCurrentTenant(AuditLogFilter filter, Pageable pageable) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("audit.tenant_required");
        }
        Specification<AuditLog> spec = build(filter).and(tenantEquals(tenantId));
        return PageResponse.of(repository.findAll(spec, pageable).map(AuditLogResponse::of));
    }

    /**
     * جست‌وجو در audit logs کل پلتفرم — فقط برای SUPER_ADMIN.
     * اگر tenantId فیلتر داده شود، محدود به همان شرکت می‌شود.
     */
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> searchAcrossTenants(Long tenantId, AuditLogFilter filter, Pageable pageable) {
        Specification<AuditLog> spec = build(filter);
        if (tenantId != null) spec = spec.and(tenantEquals(tenantId));
        return PageResponse.of(repository.findAll(spec, pageable).map(AuditLogResponse::of));
    }

    private static Specification<AuditLog> build(AuditLogFilter f) {
        return (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.event() != null && !f.event().isBlank()) {
                ps.add(cb.equal(root.get("event"), f.event().trim()));
            }
            if (f.outcome() != null) {
                ps.add(cb.equal(root.get("outcome"), f.outcome()));
            }
            if (f.userId() != null) {
                ps.add(cb.equal(root.get("userId"), f.userId()));
            }
            if (f.from() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            }
            if (f.to() != null) {
                ps.add(cb.lessThan(root.get("createdAt"), f.to()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private static Specification<AuditLog> tenantEquals(Long tenantId) {
        return (root, q, cb) -> cb.equal(root.get("tenantId"), tenantId);
    }

    /** فیلترهای اختیاری — همه null می‌توانند باشند. */
    public record AuditLogFilter(
            String event,
            AuditOutcome outcome,
            Long userId,
            Instant from,
            Instant to
    ) {}
}
