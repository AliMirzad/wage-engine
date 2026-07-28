package ir.manaz.payroll.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * دوره حقوق — بازه بسته [from, to]. معمولاً یک ماه است.
 * <p>
 * Gregorian است چون قواعد اپ روی JDK استاندارد سوارند. تبدیل Jalali
 * به Gregorian در لایه بالاتر (input adapter) انجام شود؛ محاسبه دستمزد
 * نباید به تقویم شمسی وابسته باشد چون قواعد قانون کار مستقل از تقویم‌اند.
 */
public record PayrollPeriod(LocalDate from, LocalDate to) {

    public PayrollPeriod {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from/to are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
    }

    public int totalDays() {
        return (int) (ChronoUnit.DAYS.between(from, to) + 1);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
