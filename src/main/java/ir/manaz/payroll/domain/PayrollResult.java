package ir.manaz.payroll.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * خروجی محاسبه — mutable در حین ران قواعد، سپس قفل می‌شود.
 * <p>
 * Rules ترتیبی اجرا می‌شوند و روی این نتیجه اضافه می‌کنند. آخر ران،
 * gross/net مشتق می‌شوند و نتیجه به مشتری پس داده می‌شود.
 */
public class PayrollResult {

    private final String currency;
    private final List<PayrollComponent> components = new ArrayList<>();
    private boolean sealed = false;

    public PayrollResult(String currency) {
        this.currency = currency;
    }

    public String currency() { return currency; }

    public void add(PayrollComponent component) {
        if (sealed) throw new IllegalStateException("PayrollResult is sealed");
        if (!component.amount().currency().equals(currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: expected " + currency + ", got " + component.amount().currency());
        }
        components.add(component);
    }

    public List<PayrollComponent> components() {
        return Collections.unmodifiableList(components);
    }

    /** جمع همه EARNING — پیش از کسورات. */
    public Money grossEarnings() {
        return sum(PayrollComponent.Category.EARNING);
    }

    /** جمع همه DEDUCTION. */
    public Money totalDeductions() {
        return sum(PayrollComponent.Category.DEDUCTION);
    }

    /** پرداختی خالص = gross − deductions. */
    public Money net() {
        return grossEarnings().subtract(totalDeductions());
    }

    private Money sum(PayrollComponent.Category category) {
        return components.stream()
                .filter(c -> c.category() == category)
                .map(PayrollComponent::amount)
                .reduce(Money.zero(currency), Money::add);
    }

    public void seal() { this.sealed = true; }
    public boolean isSealed() { return sealed; }
}
