package ir.manaz.payroll.domain.rule.earning;

import ir.manaz.payroll.domain.Money;
import ir.manaz.payroll.domain.PayrollComponent;
import ir.manaz.payroll.domain.PayrollContext;
import ir.manaz.payroll.domain.PayrollResult;
import ir.manaz.payroll.domain.PayrollRule;
import ir.manaz.payroll.domain.rule.WageHelper;
import org.springframework.stereotype.Component;

/** کار در تعطیل رسمی — ۱.۴ برابر نرخ ساعتی (طبق ماده ۶۲ قانون کار). */
@Component
public class HolidayWorkRule implements PayrollRule {

    private static final double MULTIPLIER = 1.4;

    @Override public int order() { return 210; }

    @Override
    public boolean appliesTo(PayrollContext ctx) {
        return ctx.attendance().holidayHours().signum() > 0;
    }

    @Override
    public void apply(PayrollContext ctx, PayrollResult result) {
        Money hourly = WageHelper.hourlyRate(ctx.contract(), ctx.currency());
        Money amount = hourly.multiply(MULTIPLIER).multiply(ctx.attendance().holidayHours());
        result.add(PayrollComponent.earning("payroll.earning.holiday_work", amount));
    }
}
