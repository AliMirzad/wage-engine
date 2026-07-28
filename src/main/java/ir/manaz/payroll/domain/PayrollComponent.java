package ir.manaz.payroll.domain;

/**
 * یک قلم دستمزد — درآمد یا کسر.
 * label: کلید قابل i18n (نه متن نمایشی) — مثلاً "payroll.earning.base".
 * category: EARNING روی gross جمع می‌شود، DEDUCTION از gross کم می‌شود، INFO فقط اطلاع‌رسانی.
 */
public record PayrollComponent(
        String label,
        Money amount,
        Category category
) {
    public enum Category { EARNING, DEDUCTION, INFO }

    public static PayrollComponent earning(String label, Money amount) {
        return new PayrollComponent(label, amount, Category.EARNING);
    }

    public static PayrollComponent deduction(String label, Money amount) {
        return new PayrollComponent(label, amount, Category.DEDUCTION);
    }

    public static PayrollComponent info(String label, Money amount) {
        return new PayrollComponent(label, amount, Category.INFO);
    }
}
