package ir.manaz.payroll.domain;

/**
 * قاعده محاسبه — یک قلم به نتیجه اضافه می‌کند یا هیچ (اگر شرایط برقرار نبود).
 * <p>
 * پیاده‌سازی‌ها باید stateless باشند و از {@link PayrollResult} فقط
 * اطلاعات public بخوانند — نه اینکه ترتیب تخصیص خاصی فرض کنند.
 * <p>
 * ترتیب اجرا با {@link #order()} کنترل می‌شود:
 *   ۱۰۰: درآمدهای پایه (حقوق، مزایا)
 *   ۲۰۰: درآمدهای متغیر (اضافه‌کاری، شب‌کاری)
 *   ۳۰۰: تعدیل درآمد (پاداش، حق مسکن قانون کار)
 *   ۵۰۰: کسورات مبتنی بر gross (بیمه)
 *   ۶۰۰: مالیات (روی gross مشمول)
 *   ۹۰۰: کسورات دیگر (وام، مساعده)
 */
public interface PayrollRule {

    /** ترتیب اجرا — کوچک‌تر جلوتر. */
    int order();

    /** برای شناسه ماشین‌خوان — در audit/log استفاده می‌شود. */
    default String name() {
        return getClass().getSimpleName();
    }

    /** false یعنی این قاعده روی این context قابل اجرا نیست. */
    default boolean appliesTo(PayrollContext context) {
        return true;
    }

    /** اجرای قاعده — روی result اقلام اضافه می‌کند. */
    void apply(PayrollContext context, PayrollResult result);
}
