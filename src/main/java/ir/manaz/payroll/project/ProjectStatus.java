package ir.manaz.payroll.project;

import java.util.EnumSet;
import java.util.Set;

/**
 * چرخه حیات پروژه.
 * <p>
 * گذارهای مجاز اینجا تعریف می‌شوند و در پاسخ API نیز برگردانده می‌شوند
 * ({@code availableTransitions})، تا فرانت لازم نباشد این منطق را تکرار کند.
 */
public enum ProjectStatus {

    /** تعریف شده ولی هنوز شروع نشده. قرارداد قابل ثبت نیست. */
    PLANNED,

    /** جاری. تنها حالتی که ثبت قرارداد جدید مجاز است. */
    ACTIVE,

    /** تعلیق موقت. قراردادهای موجود پابرجا می‌مانند ولی قرارداد جدید ثبت نمی‌شود. */
    SUSPENDED,

    /** خاتمه‌یافته. نیازمند نبودن قرارداد فعال. */
    COMPLETED,

    /** لغو شده. نیازمند نبودن قرارداد فعال. */
    CANCELLED;

    /** آیا در این وضعیت می‌توان قرارداد جدید ثبت کرد؟ */
    public boolean allowsNewContracts() {
        return this == ACTIVE;
    }

    /** آیا این یک وضعیت نهایی (بسته) است؟ */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** وضعیت‌هایی که از اینجا می‌توان به آن‌ها رفت. */
    public Set<ProjectStatus> allowedTransitions() {
        return switch (this) {
            case PLANNED   -> EnumSet.of(ACTIVE, CANCELLED);
            case ACTIVE    -> EnumSet.of(SUSPENDED, COMPLETED, CANCELLED);
            case SUSPENDED -> EnumSet.of(ACTIVE, COMPLETED, CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.of(ACTIVE);   // بازگشایی در صورت ثبت اشتباه
        };
    }

    public boolean canTransitionTo(ProjectStatus target) {
        return allowedTransitions().contains(target);
    }
}