package com.bizco.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the generated OpenAPI 3 document at {@code /v3/api-docs} and the interactive
 * Swagger UI at {@code /swagger-ui.html}, both permitted without authentication in
 * {@link SecurityConfig} since they describe the contract rather than expose business data.
 * The "Authorize" button in Swagger UI accepts the opaque session token returned by
 * {@code POST /api/v1/auth/login}.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI bizcoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bizco API")
                        .description("SME business management REST API: identity, RBAC, customers, catalog, "
                                + "and suppliers today, with sales, scheduling, inventory, and finance modules "
                                + "arriving as later MVP phases ship.")
                        .version("v1")
                        .contact(new Contact().name("Bizco")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("opaque")
                                .description("Opaque session token returned by POST /api/v1/auth/login, "
                                        + "sent as 'Authorization: Bearer <token>'.")));
    }
}
