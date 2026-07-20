package ir.manaz.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Configuration
public class MessageSourceConfig {

    public static final Locale FA = Locale.forLanguageTag("fa");

    @Bean
    public MessageSource messageSource() {
        var ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:messages");
        ms.setDefaultEncoding(StandardCharsets.UTF_8.name());
        ms.setDefaultLocale(FA);
        ms.setFallbackToSystemLocale(false);
        ms.setUseCodeAsDefaultMessage(true);
        ms.setCacheSeconds(60);
        return ms;
    }
}