package ir.manaz.payroll.domain.rule.earning;

import ir.manaz.payroll.contract.Contract;
import ir.manaz.payroll.domain.Money;
import ir.manaz.payroll.domain.PayrollComponent;
import ir.manaz.payroll.domain.PayrollContext;
import ir.manaz.payroll.domain.PayrollResult;
import ir.manaz.payroll.domain.PayrollRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * مزایای ثابت قرارداد (حق مسکن، خواربار، بن ایاب‌ذهاب، حق اولاد، سنوات، سختی).
 * <p>
 * برخلاف حقوق پایه، این‌ها فعلاً به‌طور کامل داده می‌شوند و به نسبت
 * کارکرد کاهش نمی‌یابند — اگر سیاست شرکت متفاوت است، این کلاس تعویض شود.
 * حق اولاد در سیستم واقعی باید در تعداد فرزندان کارمند ضرب شود؛ الان
 * قرارداد فقط base per-child را نگه می‌دارد و ضرب در count در rule جدا
 * قابل افزودن است.
 */
@Component
public class ContractAllowancesRule implements PayrollRule {

    @Override public int order() { return 110; }

    @Override
    public void apply(PayrollContext ctx, PayrollResult result) {
        Contract c = ctx.contract();
        String cur = ctx.currency();
        addIf(result, "payroll.earning.housing",     c.getHousingAllowance(), cur);
        addIf(result, "payroll.earning.food",        c.getFoodAllowance(), cur);
        addIf(result, "payroll.earning.transport",   c.getTransportAllowance(), cur);
        addIf(result, "payroll.earning.child",       c.getChildAllowanceBase(), cur);
        addIf(result, "payroll.earning.seniority",   c.getSeniorityPay(), cur);
        addIf(result, "payroll.earning.hardship",    c.getHardshipAllowance(), cur);
    }

    private void addIf(PayrollResult result, String label, BigDecimal value, String currency) {
        if (value == null || value.signum() <= 0) return;
        result.add(PayrollComponent.earning(label, new Money(value, currency)));
    }
}
