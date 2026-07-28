package ir.manaz.security.otp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    /**
     * تنها یک کد فعال از هر purpose برای هر کاربر معتبر است — آخرین ساخته‌شده.
     * فرض بر این است که هنگام تولید کد جدید، کدهای قدیمی همان purpose invalidate می‌شوند.
     */
    Optional<OtpCode> findTopByUserIdAndPurposeOrderByCreatedAtDesc(Long userId, OtpPurpose purpose);

    /**
     * افزایش اتمی attempts. برای جلوگیری از race شرایطی که دو تلاش همزمان
     * می‌توانند شمارش را دور بزنند.
     */
    @Modifying
    @Query("UPDATE OtpCode o SET o.attempts = o.attempts + 1 WHERE o.id = :id")
    int incrementAttempts(@Param("id") Long id);

    /**
     * invalidate کردن همه کدهای فعال یک کاربر برای یک purpose خاص — قبل از
     * تولید کد جدید تا فقط آخرین کد معتبر باشد.
     */
    @Modifying
    @Query("UPDATE OtpCode o SET o.consumedAt = :now " +
            "WHERE o.userId = :userId AND o.purpose = :purpose AND o.consumedAt IS NULL")
    int invalidateActive(@Param("userId") Long userId,
                         @Param("purpose") OtpPurpose purpose,
                         @Param("now") Instant now);

    /** invalidate کردن همه‌ی کدهای فعال یک کاربر (هر purpose) — هنگام deactivate. */
    @Modifying
    @Query("UPDATE OtpCode o SET o.consumedAt = :now " +
            "WHERE o.userId = :userId AND o.consumedAt IS NULL")
    int invalidateAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /** حذف کدهای منقضی یا مصرف‌شده — cron روزانه. */
    @Modifying
    @Query("DELETE FROM OtpCode o WHERE o.expiresAt < :now OR o.consumedAt IS NOT NULL")
    int deleteAllExpiredOrConsumed(@Param("now") Instant now);
}
