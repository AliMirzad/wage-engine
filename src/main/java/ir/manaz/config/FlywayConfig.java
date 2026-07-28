package ir.manaz.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Slf4j
@Configuration
public class FlywayConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Bean(name = "flyway")
    public Flyway flyway() {
        log.info("Configuring Flyway for {}", url);
        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        // repair قبل از migrate — اگر checksum فایل با آنچه قبلاً apply شده
        // فرق داشته باشد، schema_history را با محتوای فعلی sync می‌کند.
        // برای بار اول که V2 با یک فرمت apply و سپس فرمت‌بندی SQL همان
        // migration اصلاح شد، این مسیر خودکار حل مشکل است. بی‌ضرر روی
        // DBهای تمیز چون فقط چک می‌کند و اگر تفاوتی نبود کاری نمی‌کند.
        flyway.repair();
        flyway.migrate();
        return flyway;
    }

    /**
     * Force entityManagerFactory (auto-configured by Spring Boot) to depend on flyway,
     * so Hibernate's ddl-auto=validate runs AFTER migrations.
     */
    @Bean
    public static BeanFactoryPostProcessor entityManagerFactoryDependsOnFlyway() {
        return beanFactory -> {
            for (String name : beanFactory.getBeanDefinitionNames()) {
                if ("entityManagerFactory".equals(name)) {
                    var bd = beanFactory.getBeanDefinition(name);
                    bd.setDependsOn("flyway");
                    return;
                }
            }
        };
    }
}