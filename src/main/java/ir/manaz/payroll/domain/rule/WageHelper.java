package ir.manaz.payroll.domain.rule;

import ir.manaz.payroll.contract.Contract;
import ir.manaz.payroll.domain.Money;

import java.math.BigDecimal;

/**
 * توابع مشترک تبدیل حقوق ماهانه به روزانه/ساعتی.
 * <p>
 * فرمول‌ها بر اساس عرف قانون کار ایران است:
 *   - نرخ روزانه = ماهانه ÷ ۳۰ (نه ۲۶ — قانون کار ماه را ۳۰ روزه می‌گیرد)
 *   - نرخ ساعتی = ماهانه ÷ (ساعت هفتگی × ۴/۳۳)
 *     برای ۴۴ ساعت هفتگی → حدود ۱۹۰ ساعت در ماه
 * <p>
 * اگر تفسیر متفاوتی لازم شد (مثلاً ماه ۳۱ روزه یا ۲۶ روز کاری)،
 * این کلاس یک نقطه تغییر است.
 */
public final class WageHelper {

    private static final BigDecimal WEEKS_PER_MONTH = new BigDecimal("4.33");
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");

    private WageHelper() {}

    public static Money dailyRate(Contract c, String currency) {
        BigDecimal base = c.getBaseSalary();
        if (base == null) return Money.zero(currency);
        return new Money(base.divide(DAYS_PER_MONTH, Money.SCALE, Money.ROUNDING), currency);
    }

    public static Money hourlyRate(Contract c, String currency) {
        BigDecimal base = c.getBaseSalary();
        BigDecimal weekly = c.getWorkingHoursPerWeek();
        if (base == null || weekly == null || weekly.signum() <= 0) return Money.zero(currency);
        BigDecimal monthlyHours = weekly.multiply(WEEKS_PER_MONTH);
        return new Money(base.divide(monthlyHours, Money.SCALE, Money.ROUNDING), currency);
    }
}
