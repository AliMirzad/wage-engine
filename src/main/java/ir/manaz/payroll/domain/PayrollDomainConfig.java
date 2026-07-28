package ir.manaz.payroll.domain;

import ir.manaz.payroll.domain.tax.TaxProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TaxProperties.class)
public class PayrollDomainConfig {
}
