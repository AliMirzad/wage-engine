package ir.manaz.payroll.domain.rule.earning;

import ir.manaz.payroll.domain.Money;
import ir.manaz.payroll.domain.PayrollComponent;
import ir.manaz.payroll.domain.PayrollContext;
import ir.manaz.payroll.domain.PayrollResult;
import ir.manaz.payroll.domain.PayrollRule;
import ir.manaz.payroll.domain.rule.WageHelper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * حقوق پایه — به نسبت روزهای کارکرد در دوره.
 * پایه × (workedDays ÷ standardDays)، به‌جز غیبت غیرموجه.
 */
@Component
public class BaseSalaryRule implements PayrollRule {

    @Override public int order() { return 100; }

    @Override
    public void apply(PayrollContext ctx, PayrollResult result) {
        Money daily = WageHelper.dailyRate(ctx.contract(), ctx.currency());
        if (daily.isZero()) return;

        int workable = Math.max(0, ctx.attendance().workedDays() - ctx.attendance().unpaidAbsenceDays());
        Money amount = daily.multiply(BigDecimal.valueOf(workable));
        result.add(PayrollComponent.earning("payroll.earning.base", amount));
    }
}
