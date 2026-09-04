package com.cp.ecommerce.adapter.web.configuration;

import org.springdoc.core.models.GroupedOpenApi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Configuration exposing OpenAPI/Swagger documentation for the {@code /api} endpoints.
 */
@Configuration
public class OpenApiConfiguration {

    private static final String BEARER_AUTH_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI ecommerceOpenApi() {

        return new OpenAPI()
                .info(
                        new Info().title("Showcase application - E-commerce API")
                                .description("REST API showcasing application for placing " + "and retrieving orders.")
                                .version("v1")
                                .contact(new Contact().name("Showcase maintainers")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME_NAME))
                .schemaRequirement(
                        BEARER_AUTH_SCHEME_NAME,
                        new SecurityScheme().name(BEARER_AUTH_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"));
    }

    @Bean
    public GroupedOpenApi orderApi() {

        return GroupedOpenApi.builder().group("order").pathsToMatch("/api/order/**").build();
    }

    @Bean
    public GroupedOpenApi catalogApi() {

        return GroupedOpenApi.builder().group("catalog").pathsToMatch("/api/catalog/**").build();
    }

    @Bean
    public GroupedOpenApi inventoryApi() {

        return GroupedOpenApi.builder().group("inventory").pathsToMatch("/api/inventory/**").build();
    }

    @Bean
    public GroupedOpenApi cartApi() {

        return GroupedOpenApi.builder().group("cart").pathsToMatch("/api/cart/**").build();
    }

}
