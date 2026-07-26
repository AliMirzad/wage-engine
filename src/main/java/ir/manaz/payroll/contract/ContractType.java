package ir.manaz.payroll.contract;

/** نوع حقوقی قرارداد طبق قانون کار. */
public enum ContractType {
    /** دائم — بدون تاریخ پایان */
    PERMANENT,
    /** موقت — مدت معین */
    TEMPORARY,
    /** آزمایشی — دوره آزمایشی طبق ماده ۱۱ قانون کار */
    PROBATIONARY,
    /** پیمانکاری */
    CONTRACTOR;

    /** آیا این نوع قرارداد باید تاریخ پایان داشته باشد؟ */
    public boolean requiresEndDate() {
        return this == TEMPORARY || this == PROBATIONARY;
    }
}