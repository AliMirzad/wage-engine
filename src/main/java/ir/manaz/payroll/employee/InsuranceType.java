package ir.manaz.payroll.employee;

/** نوع بیمه — نرخ کسر سهم کارمند را تعیین می‌کند. */
public enum InsuranceType {
    MANDATORY,  // اجباری (تأمین اجتماعی)
    OPTIONAL,   // اختیاری
    EXEMPT;     // معاف از بیمه

    /** آیا از حقوق این کارمند سهم بیمه کسر می‌شود؟ */
    public boolean deductsFromPayroll() {
        return this == MANDATORY || this == OPTIONAL;
    }
}