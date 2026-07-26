package ir.manaz.payroll.employee;

/** وضعیت نظام وظیفه — برای پرونده پرسنلی مردان. */
public enum MilitaryStatus {
    COMPLETED,    // پایان خدمت
    EXEMPT,       // معاف
    LIABLE,       // مشمول
    IN_PROGRESS,  // در حال خدمت
    NOT_APPLICABLE // مشمول نیست (بانوان)
}