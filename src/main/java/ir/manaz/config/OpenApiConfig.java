package ir.manaz.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * دامنه‌های عمومی که Swagger UI به‌عنوان server URL نشان می‌دهد.
     * پیش‌فرض شامل localhost برای dev و api.manaz.pro برای prod است.
     * روی env می‌توان override کرد: OPENAPI_SERVERS=https://a.com,https://b.com
     */
    @Value("${app.openapi.servers:http://localhost:8080,https://api.manaz.pro}")
    private String servers;

    @Bean
    public OpenAPI wageEngineOpenAPI() {
        List<Server> serverList = new ArrayList<>();
        for (String url : servers.split(",")) {
            String trimmed = url.trim();
            if (!trimmed.isEmpty()) {
                serverList.add(new Server().url(trimmed).description(
                        trimmed.contains("localhost") ? "Local dev" : "Production"));
            }
        }

        return new OpenAPI()
                .info(new Info()
                        .title("Wage Engine API")
                        .description("""
                                Agile Payroll & Core Calculation Engine — Multi-tenant SaaS MVP.

                                **Auth:** JWT Bearer در header `Authorization`.
                                Access token: 5 دقیقه · Refresh token: 7 روز.

                                **Multi-tenancy:** هر request بر اساس `tenantId` داخل JWT scope می‌شود.
                                `SUPER_ADMIN` دارای `tenantId = null` و دسترسی cross-tenant است.
                                """)
                        .version("0.1.0-MVP")
                        .contact(new Contact().name("Backend Team")))
                .servers(serverList)
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token از `/api/v1/auth/login`")));
    }
}