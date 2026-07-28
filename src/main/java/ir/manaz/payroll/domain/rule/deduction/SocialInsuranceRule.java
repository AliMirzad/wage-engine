package ir.manaz.payroll.domain.rule.deduction;

import ir.manaz.payroll.domain.Money;
import ir.manaz.payroll.domain.PayrollComponent;
import ir.manaz.payroll.domain.PayrollContext;
import ir.manaz.payroll.domain.PayrollResult;
import ir.manaz.payroll.domain.PayrollRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * بیمه تأمین اجتماعی — سهم کارگر ۷٪ از دستمزد مشمول.
 * دستمزد مشمول = gross earnings (چون فعلاً همه‌ی درآمدها مشمول‌اند).
 * <p>
 * سقف مشمول در قانون وجود دارد (چند برابر حداقل حقوق) — اگر لازم شد،
 * در نسخه بعدی به‌عنوان config اضافه می‌شود.
 */
@Component
@RequiredArgsConstructor
public class SocialInsuranceRule implements PayrollRule {

    private static final BigDecimal EMPLOYEE_RATE = new BigDecimal("0.07");

    @Override public int order() { return 500; }

    @Override
    public void apply(PayrollContext ctx, PayrollResult result) {
        Money gross = result.grossEarnings();
        if (gross.isZero() || gross.isNegative()) return;
        Money insurance = gross.multiply(EMPLOYEE_RATE);
        result.add(PayrollComponent.deduction("payroll.deduction.social_insurance", insurance));
    }
}
