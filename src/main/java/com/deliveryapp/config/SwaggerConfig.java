package com.deliveryapp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class SwaggerConfig {

        @Bean
        public OpenAPI openAPI() {
                final String securitySchemeName = "bearerAuth";

                return new OpenAPI()
                                .servers(List.of(
                                                new Server().url("https://allin-shops.com"),
                                                new Server().url("http://localhost:8080")))
                                .info(new Info()
                                                .title("Allin API")
                                                .description("REST API documentation forAllin")
                                                .version("1.0.0")
                                                .contact(new Contact()
                                                                .name("ENG.Mohahamad Allaith Mojadamy")
                                                                .email("mohamadallays.mg@email.com")))
                                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                                .components(new Components()
                                                .addSecuritySchemes(securitySchemeName,
                                                                new SecurityScheme()
                                                                                .name(securitySchemeName)
                                                                                .type(SecurityScheme.Type.HTTP)
                                                                                .scheme("bearer")
                                                                                .bearerFormat("JWT")));
        }
}