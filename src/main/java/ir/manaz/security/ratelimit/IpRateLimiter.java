package ir.manaz.security.ratelimit;

import ir.manaz.config.AppSecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * محدودکننده نرخ درخواست بر پایه IP و «bucket» یک دقیقه‌ای.
 * <p>
 * نگه‌داری وضعیت در حافظه پروسه است — برای استقرار تک‌نمونه‌ای مناسب است.
 * در توپولوژی چندنمونه‌ای، دقیقاً همان محدودیت روی هر نمونه به‌طور مستقل
 * اعمال می‌شود (سقف واقعی = N × limit)، که برای auth کافی است چون
 * برای همه، سربار Redis را توجیه نمی‌کند. برای رفتن به prod چندنمونه‌ای
 * می‌توان این کلاس را با پیاده‌سازی Redis-based جایگزین کرد.
 * <p>
 * پاک‌سازی buckets قدیمی هر دقیقه اجرا می‌شود تا رشد بدون سقف حافظه
 * (نگرانی DoS از سمت خود rate limiter) پیش نیاید.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpRateLimiter {

    private final AppSecurityProperties props;

    private final Map<Key, AtomicInteger> counters = new ConcurrentHashMap<>();

    /** true اگر مجاز باشد. false یعنی سقف پر است — به کاربر 429 برگردانده شود. */
    public boolean tryAcquire(String ip, RateLimitCategory category) {
        if (ip == null || ip.isBlank()) return true;
        int limit = switch (category) {
            case AUTH -> props.getRateLimit().getAuthPerMinute();
            case FORGOT_PASSWORD -> props.getRateLimit().getForgotPasswordPerMinute();
        };
        if (limit <= 0) return true;

        Key key = new Key(ip, category, currentMinute());
        AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger());
        int newCount = counter.incrementAndGet();
        return newCount <= limit;
    }

    private static long currentMinute() {
        return Instant.now().getEpochSecond() / 60;
    }

    /** حذف bucketهای دقیقه‌های گذشته تا حافظه رشد بی‌سقف نکند. */
    @Scheduled(fixedRate = 60_000L)
    void purgeOld() {
        long cutoff = currentMinute() - 1;
        int before = counters.size();
        counters.keySet().removeIf(k -> k.minute() < cutoff);
        int removed = before - counters.size();
        if (removed > 0) log.debug("Purged {} stale rate-limit buckets", removed);
    }

    public enum RateLimitCategory { AUTH, FORGOT_PASSWORD }

    private record Key(String ip, RateLimitCategory category, long minute) {}
}
