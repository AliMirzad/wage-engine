package ir.manaz.payroll.domain.tax;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * پله‌های مالیات حقوق. هر سال با بخشنامه سازمان امور مالیاتی تغییر می‌کند —
 * از application.yml خوانده می‌شود تا نیاز به deploy مجدد نباشد.
 * <p>
 * مثال (پیش‌فرض تقریبی برای ۱۴۰۳ — قبل از استقرار prod با آخرین ابلاغیه
 * سازمان امور مالیاتی صحه‌گذاری شود):
 * <pre>
 * app.payroll.tax.brackets:
 *   - { threshold: 120000000, rate: 0.00 }   # معاف تا ۱۲۰ میلیون
 *   - { threshold: 165000000, rate: 0.10 }
 *   - { threshold: 270000000, rate: 0.15 }
 *   - { threshold: 400000000, rate: 0.20 }
 *   - { threshold: null,      rate: 0.30 }   # مازاد
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.payroll.tax")
public class TaxProperties {

    private List<Bracket> brackets = defaultBrackets();

    public List<TaxBracket> toBrackets() {
        return brackets.stream()
                .map(b -> new TaxBracket(b.threshold, b.rate))
                .toList();
    }

    private static List<Bracket> defaultBrackets() {
        List<Bracket> list = new ArrayList<>();
        // مقادیر پیش‌فرض placeholder — قبل از prod با ابلاغیه رسمی به‌روز شود.
        list.add(new Bracket(new BigDecimal("120000000"), new BigDecimal("0.00")));
        list.add(new Bracket(new BigDecimal("165000000"), new BigDecimal("0.10")));
        list.add(new Bracket(new BigDecimal("270000000"), new BigDecimal("0.15")));
        list.add(new Bracket(new BigDecimal("400000000"), new BigDecimal("0.20")));
        list.add(new Bracket(null, new BigDecimal("0.30")));
        return list;
    }

    @Getter @Setter
    public static class Bracket {
        private BigDecimal threshold;
        private BigDecimal rate;

        public Bracket() {}
        public Bracket(BigDecimal threshold, BigDecimal rate) {
            this.threshold = threshold;
            this.rate = rate;
        }
    }
}
