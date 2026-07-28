package ir.manaz.payroll.domain.tax;

import java.math.BigDecimal;

/**
 * یک پله مالیاتی. threshold سقف بالای این پله بر حسب دستمزد ماهانه؛
 * null یعنی «تا بی‌نهایت» (آخرین پله). rate سهم به‌صورت اعشاری (۰.۱ = ۱۰٪).
 */
public record TaxBracket(BigDecimal threshold, BigDecimal rate) {
    public TaxBracket {
        if (rate == null || rate.signum() < 0) {
            throw new IllegalArgumentException("rate must be >= 0");
        }
    }
}
