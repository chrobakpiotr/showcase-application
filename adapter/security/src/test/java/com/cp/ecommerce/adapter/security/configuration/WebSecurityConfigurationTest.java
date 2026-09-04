package com.cp.ecommerce.adapter.security.configuration;

import com.cp.ecommerce.adapter.SecurityTestConfiguration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class checking that {@link WebSecurityConfiguration} enforces authentication and role based authorization for the order
 * API, while keeping documentation endpoints publicly accessible.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(SecurityTestConfiguration.class)
class WebSecurityConfigurationTest {

    private static final String ORDER_ENDPOINT = "/api/order";

    private static final String CATALOG_PRODUCTS_ENDPOINT = "/api/catalog/products";

    private static final String INVENTORY_ENDPOINT = "/api/inventory/SKU-1234";

    @Autowired
    private transient MockMvc mockMvc;

    @Test
    void shouldAllowUnauthenticatedAccessToOpenApiDocumentation() throws Exception {

        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void shouldSetContentSecurityPolicyHeaderOnEveryResponse() throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")));
    }

    @Test
    void shouldAllowConnectSrcToKeycloaksOwnOriginForDirectBrowserLogin() throws Exception {

        // The Angular frontend authenticates against Keycloak's token endpoint directly from the browser (see
        // AuthService.login), not through this backend - without this, connect-src 'self' would silently block
        // that login request.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string("Content-Security-Policy", containsString("connect-src 'self' http://localhost:8081")));
    }

    @Test
    void shouldAllowUnauthenticatedAccessToFrontendStaticAssets() throws Exception {

        final int status = mockMvc.perform(get("/index.html")).andReturn().getResponse().getStatus();

        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void shouldRejectUnauthenticatedAccessToOrderApi() throws Exception {

        mockMvc.perform(get(ORDER_ENDPOINT + "/some-order")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ORDER_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectFindingOrderWithoutReadRole() throws Exception {

        mockMvc.perform(get(ORDER_ENDPOINT + "/some-order").with(jwt().authorities(() -> "ROLE_ORDER_WRITE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowFindingOrderWithReadRole() throws Exception {

        final int status = mockMvc.perform(get(ORDER_ENDPOINT + "/some-order").with(jwt().authorities(() -> "ROLE_ORDER_READ")))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void shouldRejectPlacingOrderWithoutWriteRole() throws Exception {

        mockMvc.perform(
                post(ORDER_ENDPOINT).with(jwt().authorities(() -> "ROLE_ORDER_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowPlacingOrderWithWriteRole() throws Exception {

        // This is a security-authorization test, not a content-validation test: the empty "{}" body deliberately
        // fails domain validation further downstream (now correctly a 400, not a 500), which is irrelevant here -
        // all that matters is the request cleared the security filter chain instead of being rejected as 401/403.
        final int status = mockMvc
                .perform(
                        post(ORDER_ENDPOINT).with(jwt().authorities(() -> "ROLE_ORDER_WRITE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void shouldRejectUnauthenticatedAccessToCatalogApi() throws Exception {

        mockMvc.perform(get(CATALOG_PRODUCTS_ENDPOINT)).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectListingProductsWithoutCatalogReadRole() throws Exception {

        mockMvc.perform(get(CATALOG_PRODUCTS_ENDPOINT).with(jwt().authorities(() -> "ROLE_CATALOG_WRITE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowListingProductsWithCatalogReadRole() throws Exception {

        mockMvc.perform(get(CATALOG_PRODUCTS_ENDPOINT).with(jwt().authorities(() -> "ROLE_CATALOG_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectCreatingProductWithoutCatalogWriteRole() throws Exception {

        mockMvc.perform(
                post(CATALOG_PRODUCTS_ENDPOINT).with(jwt().authorities(() -> "ROLE_CATALOG_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowCreatingProductWithCatalogWriteRole() throws Exception {

        // Security-authorization test only: the empty "{}" body will fail downstream category resolution, which is
        // irrelevant here - all that matters is the request cleared the security filter chain.
        final int status = mockMvc
                .perform(
                        post(CATALOG_PRODUCTS_ENDPOINT).with(jwt().authorities(() -> "ROLE_CATALOG_WRITE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void shouldRejectUnauthenticatedAccessToInventoryApi() throws Exception {

        mockMvc.perform(get(INVENTORY_ENDPOINT)).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectReadingStockLevelWithoutInventoryReadRole() throws Exception {

        mockMvc.perform(get(INVENTORY_ENDPOINT).with(jwt().authorities(() -> "ROLE_INVENTORY_WRITE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowReadingStockLevelWithInventoryReadRole() throws Exception {

        mockMvc.perform(get(INVENTORY_ENDPOINT).with(jwt().authorities(() -> "ROLE_INVENTORY_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectReceivingStockWithoutInventoryWriteRole() throws Exception {

        mockMvc.perform(
                post(INVENTORY_ENDPOINT + "/receive").with(jwt().authorities(() -> "ROLE_INVENTORY_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowReceivingStockWithInventoryWriteRole() throws Exception {

        final int status = mockMvc
                .perform(
                        post(INVENTORY_ENDPOINT + "/receive").with(jwt().authorities(() -> "ROLE_INVENTORY_WRITE"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":5}"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isNotIn(401, 403);
    }

}
