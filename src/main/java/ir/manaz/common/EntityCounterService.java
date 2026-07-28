package ir.manaz.common;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * تخصیص اتمی شماره‌های سریالی به‌ازای (شرکت، نوع موجودیت).
 * <p>
 * برای جلوگیری از race شماره‌های کد پرسنلی و شماره قرارداد که قبلاً با
 * {@code MAX(seq)+1} محاسبه می‌شدند. UPSERT با RETURNING در PostgreSQL تحت
 * قفل ردیف اتمی است، بنابراین چندین تراکنش همزمان هر یک شماره‌ی یکتا می‌گیرند.
 * <p>
 * روی {@link Propagation#REQUIRES_NEW} اجرا می‌شود تا شماره حتی در صورت
 * rollback تراکنش والد (مثلاً هنگام برخورد با تخلف unique constraint) commit
 * بماند و در فراخوانی بعدی duplicate تولید نشود. عوارض: در صورت رخداد نادر
 * rollback، در توالی شماره‌ها gap ایجاد می‌شود که برای شماره‌های نمایشی
 * قابل قبول است.
 */
@Service
@RequiredArgsConstructor
public class EntityCounterService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long nextValue(long tenantId, String entityType) {
        Long value = jdbcTemplate.queryForObject("""
                INSERT INTO entity_counters (tenant_id, entity_type, current_value)
                VALUES (?, ?, 1)
                ON CONFLICT (tenant_id, entity_type)
                DO UPDATE SET current_value = entity_counters.current_value + 1
                RETURNING current_value
                """, Long.class, tenantId, entityType);
        if (value == null) {
            throw new IllegalStateException(
                    "Failed to allocate counter for tenant=" + tenantId + " type=" + entityType);
        }
        return value;
    }
}
