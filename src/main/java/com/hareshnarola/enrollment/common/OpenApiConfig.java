package com.hareshnarola.enrollment.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI enrollmentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Enrollment Service API")
                        .version("v1")
                        .description("Courses, enrollments and waitlists. Log in via /api/auth/login and "
                                + "send the token as 'Authorization: Bearer <token>'.")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
