package ir.manaz.payroll.domain.rule.earning;

import ir.manaz.payroll.domain.Money;
import ir.manaz.payroll.domain.PayrollComponent;
import ir.manaz.payroll.domain.PayrollContext;
import ir.manaz.payroll.domain.PayrollResult;
import ir.manaz.payroll.domain.PayrollRule;
import ir.manaz.payroll.domain.rule.WageHelper;
import org.springframework.stereotype.Component;

/**
 * شب‌کاری — طبق قانون کار ۳۵٪ اضافه بر نرخ ساعتی عادی
 * فقط برای ساعات بین ۲۲ تا ۶ صبح که کارگر شیفتی نیست.
 */
@Component
public class NightShiftRule implements PayrollRule {

    private static final double PREMIUM = 0.35;

    @Override public int order() { return 220; }

    @Override
    public boolean appliesTo(PayrollContext ctx) {
        return ctx.attendance().nightShiftHours().signum() > 0;
    }

    @Override
    public void apply(PayrollContext ctx, PayrollResult result) {
        Money hourly = WageHelper.hourlyRate(ctx.contract(), ctx.currency());
        Money amount = hourly.multiply(PREMIUM).multiply(ctx.attendance().nightShiftHours());
        result.add(PayrollComponent.earning("payroll.earning.night_shift", amount));
    }
}
