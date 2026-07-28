package ir.manaz.security.otp;

import ir.manaz.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * تولید و راستی‌آزمایی کد OTP.
 * <ul>
 *   <li>کد ۶ رقمی از {@link SecureRandom} تولید می‌شود.</li>
 *   <li>در DB فقط hash (SHA-256 hex) ذخیره می‌شود — raw فقط در ایمیل.</li>
 *   <li>هر purpose scope مستقل دارد.</li>
 *   <li>تولید کد جدید همه‌ی کدهای فعال قبلی همان purpose برای همان کاربر را invalidate می‌کند.</li>
 *   <li>سقف تلاش (پیش‌فرض ۵) و انقضا (پیش‌فرض ۱۰ دقیقه) در برابر brute-force حفاظت می‌کند.</li>
 *   <li>مقایسه‌ی hash با {@link MessageDigest#isEqual(byte[], byte[])} constant-time است.</li>
 * </ul>
 */
@Slf4j
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_LENGTH = 6;
    private static final int DEFAULT_TTL_MINUTES = 10;
    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final OtpCodeRepository repository;
    /** self-reference برای دسترسی به proxy تراکنش‌دار در recordFailedAttempt. */
    private final OtpService self;

    public OtpService(OtpCodeRepository repository, @Lazy OtpService self) {
        this.repository = repository;
        this.self = self;
    }

    /**
     * کد جدید برای یک کاربر و purpose تولید و ذخیره می‌کند و raw code را برمی‌گرداند
     * تا کالر (سرویس ایمیل) آن را برای کاربر بفرستد. کدهای قبلی همان purpose invalidate می‌شوند.
     */
    @Transactional
    public String issue(Long userId, OtpPurpose purpose) {
        Instant now = Instant.now();
        repository.invalidateActive(userId, purpose, now);

        String rawCode = generateNumericCode(DEFAULT_LENGTH);
        OtpCode entity = OtpCode.builder()
                .userId(userId)
                .codeHash(sha256Hex(rawCode))
                .purpose(purpose)
                .attempts(0)
                .maxAttempts(DEFAULT_MAX_ATTEMPTS)
                .createdAt(now)
                .expiresAt(now.plus(DEFAULT_TTL_MINUTES, ChronoUnit.MINUTES))
                .build();
        repository.save(entity);
        return rawCode;
    }

    /**
     * راستی‌آزمایی کد. در موفقیت کد مصرف می‌شود.
     * در شکست، شمارنده‌ی تلاش در تراکنش جدا افزایش می‌یابد (تا rollback اثرش را پاک نکند)
     * و {@link UnauthorizedException} می‌اندازد.
     * از سه حالت شکست، همه پیام یکسان می‌گیرند تا اطلاعاتی به مهاجم نرسد.
     */
    @Transactional
    public void verifyAndConsume(Long userId, OtpPurpose purpose, String rawCode) {
        OtpCode otp = repository.findTopByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)
                .orElseThrow(() -> new UnauthorizedException("otp.invalid"));

        if (!otp.isVerifiable()) {
            throw new UnauthorizedException("otp.invalid");
        }

        boolean match = rawCode != null
                && MessageDigest.isEqual(
                        sha256Hex(rawCode).getBytes(StandardCharsets.UTF_8),
                        otp.getCodeHash().getBytes(StandardCharsets.UTF_8));
        if (!match) {
            // از طریق self (proxy) صدا زده می‌شود تا REQUIRES_NEW فعال شود.
            // با self-invocation ساده (this.recordFailedAttempt) proxy بایپَس و
            // increment بی‌اثر می‌ماند چون rollback والد آن را هم پاک می‌کند.
            self.recordFailedAttempt(otp.getId());
            throw new UnauthorizedException("otp.invalid");
        }

        otp.setConsumedAt(Instant.now());
        repository.save(otp);
    }

    /** ثبت مستقل تلاش ناموفق — از تراکنش والد جدا تا rollback آن این را پاک نکند. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(Long otpId) {
        repository.incrementAttempts(otpId);
    }

    /** invalidate کردن همه‌ی کدهای فعال یک کاربر — هنگام deactivate یا password change. */
    @Transactional
    public void invalidateAllForUser(Long userId) {
        repository.invalidateAllForUser(userId, Instant.now());
    }

    /** پاکسازی روزانه. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpired() {
        int n = repository.deleteAllExpiredOrConsumed(Instant.now());
        if (n > 0) log.info("Purged {} expired/consumed OTP codes", n);
    }

    /** ثابت‌طول عددی — «000123» هم مجاز است. */
    private static String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
