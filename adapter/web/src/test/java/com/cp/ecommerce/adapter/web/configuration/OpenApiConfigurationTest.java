package com.cp.ecommerce.adapter.web.configuration;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class checking {@link OpenApiConfiguration}'s bean definitions.
 */
class OpenApiConfigurationTest {

    private final transient OpenApiConfiguration configuration = new OpenApiConfiguration();

    @Test
    void shouldDefineOpenApiWithBearerAuthSecurityScheme() {

        final OpenAPI openApi = configuration.ecommerceOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Showcase application - E-commerce API");
        assertThat(openApi.getSecurity()).hasSize(1);
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
    }

    @Test
    void shouldGroupOrderApiEndpoints() {

        assertThat(configuration.orderApi().getGroup()).isEqualTo("order");
    }

    @Test
    void shouldGroupCatalogApiEndpoints() {

        assertThat(configuration.catalogApi().getGroup()).isEqualTo("catalog");
    }

}
