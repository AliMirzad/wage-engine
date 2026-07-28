package ir.manaz.payroll.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * مقدار پولی همراه ارز. جمع/تفریق روی ارزهای متفاوت مجاز نیست —
 * برخورد صریح در طراحی، تا اشتباهاتی مثل جمع ریال و دلار در محاسبات
 * حقوق سریع تشخیص داده شود.
 * <p>
 * scale ثابت روی ۴ رقم اعشار — همان قرارداد پایگاه داده (NUMERIC(19,4)).
 * ذخیره تصمیم‌گیری rounding و scale در یک نقطه.
 */
public record Money(BigDecimal amount, String currency) implements Comparable<Money> {

    public static final int SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (currency.isBlank()) throw new IllegalArgumentException("currency must not be blank");
        amount = amount.setScale(SCALE, ROUNDING);
    }

    public static Money of(long amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }

    public Money multiply(double factor) {
        return multiply(BigDecimal.valueOf(factor));
    }

    public boolean isPositive() { return amount.signum() > 0; }
    public boolean isZero()     { return amount.signum() == 0; }
    public boolean isNegative() { return amount.signum() < 0; }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public int compareTo(Money o) {
        requireSameCurrency(o);
        return amount.compareTo(o.amount);
    }
}
