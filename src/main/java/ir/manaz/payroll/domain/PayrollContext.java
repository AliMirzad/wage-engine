package ir.manaz.payroll.domain;

import ir.manaz.payroll.contract.Contract;
import lombok.Builder;

/**
 * ورودی محاسبه دستمزد یک قرارداد در یک دوره. read-only.
 * <p>
 * جدا از Entity Contract نگه داشته می‌شود تا domain layer به JPA وابسته
 * نباشد و بشود در تست‌ها ساده mock کرد. ولی الان به‌دلایل عملی مستقیماً
 * Contract را می‌گیرد چون قواعد نیاز به تمام اعداد قرارداد دارند —
 * اگر بعداً decoupling کامل خواستیم، یک PayrollSnapshot جای Contract
 * می‌نشیند.
 */
@Builder
public record PayrollContext(
        Contract contract,
        PayrollPeriod period,
        Attendance attendance,
        String currency
) {
    public PayrollContext {
        if (contract == null || period == null || attendance == null) {
            throw new IllegalArgumentException("contract/period/attendance are required");
        }
        if (currency == null || currency.isBlank()) {
            currency = contract.getCurrency() != null ? contract.getCurrency() : "IRR";
        }
    }
}
