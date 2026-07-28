package ir.manaz.payroll.domain.rule.earning;

import ir.manaz.payroll.domain.Money;
import ir.manaz.payroll.domain.PayrollComponent;
import ir.manaz.payroll.domain.PayrollContext;
import ir.manaz.payroll.domain.PayrollResult;
import ir.manaz.payroll.domain.PayrollRule;
import ir.manaz.payroll.domain.rule.WageHelper;
import org.springframework.stereotype.Component;

/**
 * اضافه‌کاری روزهای عادی — قانون کار: ۱.۴ برابر نرخ ساعتی.
 * اگر سیاست شرکت ضریب دیگری دارد (بعضی جاها ۱.۲۵)، این کلاس تعویض شود.
 */
@Component
public class OvertimeRule implements PayrollRule {

    private static final double MULTIPLIER = 1.4;

    @Override public int order() { return 200; }

    @Override
    public boolean appliesTo(PayrollContext ctx) {
        return ctx.attendance().overtimeHours().signum() > 0;
    }

    @Override
    public void apply(PayrollContext ctx, PayrollResult result) {
        Money hourly = WageHelper.hourlyRate(ctx.contract(), ctx.currency());
        Money amount = hourly.multiply(MULTIPLIER).multiply(ctx.attendance().overtimeHours());
        result.add(PayrollComponent.earning("payroll.earning.overtime", amount));
    }
}
