package ir.manaz.payroll.employee;

/** نوع همکاری — مبنای محاسبه حقوق را تعیین می‌کند. */
public enum EmploymentType {
    FULL_TIME,   // تمام‌وقت
    PART_TIME,   // پاره‌وقت
    CONTRACT,    // پیمانی
    DAILY_WAGE,  // روزمزد
    TEMPORARY;   // موقت

    /** آیا مبنای محاسبه روزانه است (نه ماهانه)؟ */
    public boolean isDailyBased() {
        return this == DAILY_WAGE;
    }
}