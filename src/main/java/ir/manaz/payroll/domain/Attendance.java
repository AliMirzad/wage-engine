package ir.manaz.payroll.domain;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * اطلاعات کارکرد کارمند در یک دوره — ورودی محاسبه دستمزد.
 * <p>
 * از پرزنس/حضورغیاب جمع‌آوری می‌شود و به calculator پاس داده می‌شود.
 * calculator خودش نمی‌داند این اعداد چطور به دست آمده‌اند.
 */
@Builder
public record Attendance(
        /* روزهای کاری واقعی در دوره — پایه محاسبه حقوق پایه است. */
        int workedDays,

        /* روزهای کاری تعریف شده در دوره — معمولاً ۲۶ یا ۳۰. */
        int standardDays,

        /* ساعات کار عادی. */
        BigDecimal regularHours,

        /* ساعات اضافه‌کاری (روزهای عادی، خارج از ساعت موظفی). */
        BigDecimal overtimeHours,

        /* ساعات کار در تعطیلات رسمی. */
        BigDecimal holidayHours,

        /* ساعات کار شب‌کاری. */
        BigDecimal nightShiftHours,

        /* روزهای مرخصی استحقاقی استفاده شده. */
        int paidLeaveDays,

        /* روزهای غیبت غیرموجه — از حقوق پایه کسر می‌شود. */
        int unpaidAbsenceDays
) {
    public Attendance {
        if (workedDays < 0 || standardDays <= 0) {
            throw new IllegalArgumentException("Invalid day counts");
        }
        if (regularHours == null) regularHours = BigDecimal.ZERO;
        if (overtimeHours == null) overtimeHours = BigDecimal.ZERO;
        if (holidayHours == null) holidayHours = BigDecimal.ZERO;
        if (nightShiftHours == null) nightShiftHours = BigDecimal.ZERO;
    }

    /** حالت متداول — ماه کامل بدون اضافه‌کاری/غیبت. */
    public static Attendance fullMonth(int standardDays) {
        return Attendance.builder()
                .workedDays(standardDays)
                .standardDays(standardDays)
                .regularHours(BigDecimal.ZERO)
                .overtimeHours(BigDecimal.ZERO)
                .holidayHours(BigDecimal.ZERO)
                .nightShiftHours(BigDecimal.ZERO)
                .paidLeaveDays(0)
                .unpaidAbsenceDays(0)
                .build();
    }
}
