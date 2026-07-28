package ir.manaz.payroll.domain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Orchestrator محاسبه دستمزد. همه پیاده‌سازی‌های {@link PayrollRule}
 * به‌عنوان bean کشف و به ترتیب {@link PayrollRule#order()} اجرا می‌شوند.
 * <p>
 * Rules در Spring context ثبت می‌شوند — افزودن قاعده جدید فقط ساخت
 * یک bean است و نیاز به تغییر calculator نیست.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollCalculator {

    private final List<PayrollRule> rules;

    public PayrollResult calculate(PayrollContext context) {
        PayrollResult result = new PayrollResult(context.currency());

        rules.stream()
                .sorted(Comparator.comparingInt(PayrollRule::order))
                .filter(r -> r.appliesTo(context))
                .forEach(r -> {
                    log.debug("Applying rule {} for contract {}", r.name(), context.contract().getId());
                    r.apply(context, result);
                });

        result.seal();
        return result;
    }
}
