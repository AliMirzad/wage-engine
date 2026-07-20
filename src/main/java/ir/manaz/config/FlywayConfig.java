package ir.manaz.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * Flyway bean — runs migrations at bean creation time (initMethod = "migrate").
     * Any bean that depends on the DB schema being present must add
     * {@code @DependsOn("flyway")}.
     */
    @Bean(name = "flyway", initMethod = "migrate")
    public Flyway flyway() {
        log.info("Configuring Flyway for {}", url);
        return Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }

    /**
     * Ensures the JPA EntityManagerFactory (which runs Hibernate's
     * ddl-auto=validate) is created AFTER Flyway has migrated the schema.
     */
    @Bean
    @DependsOn("flyway")
    public EntityManagerFactoryBuilderPlaceholder entityManagerFactoryDependency() {
        return new EntityManagerFactoryBuilderPlaceholder();
    }

    /** Marker bean; existence forces @DependsOn ordering. */
    static class EntityManagerFactoryBuilderPlaceholder {}
}