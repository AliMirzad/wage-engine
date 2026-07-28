package ir.manaz.audit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public final class AuditLogDtos {

    private AuditLogDtos() {}

    @Schema(description = "یک ردیف audit log — تغییرناپذیر است")
    public record AuditLogResponse(
            Long id,
            @Schema(description = "شرکت مربوطه؛ برای رویدادهای پلتفرمی null") Long tenantId,
            @Schema(description = "کاربر انجام‌دهنده؛ برای login ناموفق روی حساب ناشناخته null") Long userId,
            @Schema(description = "نام کاربری در زمان رویداد — snapshot") String username,
            @Schema(example = "USER_CREATED") String event,
            @Schema(example = "SUCCESS", description = "SUCCESS | FAILURE | DENIED") AuditOutcome outcome,
            @Schema(description = "نوع موجودیت هدف؛ اختیاری", example = "Contract") String targetType,
            @Schema(description = "شناسه‌ی موجودیت هدف؛ اختیاری", example = "42") String targetId,
            @Schema(description = "متن انسان‌خوان جزئیات؛ ممکن است null باشد") String details,
            @Schema(description = "IP سرچشمه؛ ممکن است null باشد") String ipAddress,
            @Schema(description = "User-Agent مرورگر/کلاینت؛ ممکن است null باشد") String userAgent,
            Instant createdAt
    ) {
        public static AuditLogResponse of(AuditLog a) {
            return new AuditLogResponse(
                    a.getId(), a.getTenantId(), a.getUserId(), a.getUsername(),
                    a.getEvent(), a.getOutcome(),
                    a.getTargetType(), a.getTargetId(),
                    a.getDetails(), a.getIpAddress(), a.getUserAgent(),
                    a.getCreatedAt()
            );
        }
    }
}
