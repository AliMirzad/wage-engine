package ir.manaz.payroll.domain.rule.deduction;

import ir.manaz.payroll.domain.Money;
import ir.manaz.payroll.domain.PayrollComponent;
import ir.manaz.payroll.domain.PayrollContext;
import ir.manaz.payroll.domain.PayrollResult;
import ir.manaz.payroll.domain.PayrollRule;
import ir.manaz.payroll.domain.tax.TaxBracket;
import ir.manaz.payroll.domain.tax.TaxProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * مالیات حقوق پلکانی — روی درآمد مشمول پس از کسر بیمه اعمال می‌شود.
 * پله‌ها از {@link TaxProperties} خوانده می‌شوند.
 * <p>
 * الگوریتم: مبلغ مشمول را از پایین‌ترین پله تا بالا تکه‌تکه می‌کنیم و
 * هر تکه را در نرخ خودش ضرب می‌کنیم. آخرین پله (threshold=null)
 * بی‌سقف است و نرخ نهایی روی مازاد اعمال می‌شود.
 */
@Component
@RequiredArgsConstructor
public class IncomeTaxRule implements PayrollRule {

    private final TaxProperties taxProperties;

    @Override public int order() { return 600; }

    @Override
    public void apply(PayrollContext ctx, PayrollResult result) {
        // درآمد مشمول = gross − بیمه (که با order=500 قبلاً اضافه شده).
        Money taxable = result.grossEarnings().subtract(result.totalDeductions());
        if (taxable.isZero() || taxable.isNegative()) return;

        BigDecimal amount = taxable.amount();
        BigDecimal owed = BigDecimal.ZERO;
        BigDecimal covered = BigDecimal.ZERO;

        for (TaxBracket b : taxProperties.toBrackets()) {
            BigDecimal top = b.threshold();
            BigDecimal slice;
            if (top == null) {
                slice = amount.subtract(covered).max(BigDecimal.ZERO);
            } else {
                if (amount.compareTo(covered) <= 0) break;
                BigDecimal upTo = top.min(amount);
                slice = upTo.subtract(covered);
                if (slice.signum() <= 0) continue;
            }
            owed = owed.add(slice.multiply(b.rate()));
            if (top == null) break;
            covered = top;
            if (covered.compareTo(amount) >= 0) break;
        }

        if (owed.signum() <= 0) return;
        result.add(PayrollComponent.deduction(
                "payroll.deduction.income_tax", new Money(owed, ctx.currency())));
    }
}
