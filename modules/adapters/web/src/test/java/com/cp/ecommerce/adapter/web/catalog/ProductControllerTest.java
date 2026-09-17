package com.cp.ecommerce.adapter.web.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ProductBuilder;
import com.cp.ecommerce.adapter.web.catalog.mapper.ProductWebMapper;
import com.cp.ecommerce.adapter.web.catalog.metrics.ProductMetrics;
import com.cp.ecommerce.adapter.web.catalog.resource.ProductDetailsResource;
import com.cp.ecommerce.adapter.web.utils.ProductResourceBuilder;
import com.cp.ecommerce.domain.catalog.CategoryNotFoundException;
import com.cp.ecommerce.domain.catalog.PagedResult;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.ProductPageQuery;
import com.cp.ecommerce.domain.catalog.usecase.ListProductsUseCase;
import com.cp.ecommerce.domain.catalog.usecase.ManageProductUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static com.cp.ecommerce.adapter.common.utils.ProductBuilder.TEST_PRODUCT_SKU;

/**
 * Test class checking product controller's behavior and product API response.
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    private static final String TEST_DRAFT_NAME = "name";

    private static final String PRODUCTS_ENDPOINT = "/api/catalog/products";

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient ListProductsUseCase listProductsUseCase;

    @MockitoBean
    private transient ManageProductUseCase manageProductUseCase;

    @MockitoBean
    private transient ProductWebMapper productWebMapper;

    @MockitoBean
    private transient ProductMetrics productMetrics;

    @Test
    void shouldListProductsWithDefaultPaging() throws Exception {

        final Product product = ProductBuilder.mockProduct();
        given(listProductsUseCase.listProducts(new ProductPageQuery(0, 20, null, false)))
                .willReturn(new PagedResult<>(List.of(product), 0, 20, 1, 1));
        given(productWebMapper.mapToResource(product)).willReturn(Optional.of(mockProductDetailsResource()));

        this.mockMvc.perform(get(PRODUCTS_ENDPOINT))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/hal+json"))
                .andExpect(jsonPath("$._embedded.productDetailsResourceList[0].sku").value(TEST_PRODUCT_SKU))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(
                        jsonPath("$._links.self.href", containsString("/api/catalog/products?page=0&size=20&activeOnly=false")))
                .andExpect(jsonPath("$._links.first").exists())
                .andExpect(jsonPath("$._links.last").exists())
                .andExpect(jsonPath("$._links.prev").doesNotExist())
                .andExpect(jsonPath("$._links.next").doesNotExist());
    }

    @Test
    void shouldIncludePrevAndNextLinksForMiddlePage() throws Exception {

        given(listProductsUseCase.listProducts(new ProductPageQuery(1, 10, "electronics", true)))
                .willReturn(new PagedResult<>(List.of(), 1, 10, 30, 3));

        this.mockMvc
                .perform(
                        get(PRODUCTS_ENDPOINT).param("page", "1")
                                .param("size", "10")
                                .param("category", "electronics")
                                .param("activeOnly", "true"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$._links.prev.href",
                                endsWith("/api/catalog/products?page=0&size=10&category=electronics&activeOnly=true")))
                .andExpect(
                        jsonPath(
                                "$._links.next.href",
                                endsWith("/api/catalog/products?page=2&size=10&category=electronics&activeOnly=true")));
    }

    @Test
    void shouldRejectNegativePage() throws Exception {

        this.mockMvc.perform(get(PRODUCTS_ENDPOINT).param("page", "-1"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(listProductsUseCase, never()).listProducts(any());
    }

    @Test
    void shouldRejectSizeAboveMax() throws Exception {

        this.mockMvc.perform(get(PRODUCTS_ENDPOINT).param("size", String.valueOf(ProductPageQuery.MAX_SIZE + 1)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(listProductsUseCase, never()).listProducts(any());
    }

    @Test
    void shouldFindProductBySku() throws Exception {

        final Product product = ProductBuilder.mockProduct();
        given(manageProductUseCase.findProduct(TEST_PRODUCT_SKU)).willReturn(product);
        given(productWebMapper.mapToResource(product)).willReturn(Optional.of(mockProductDetailsResource()));

        this.mockMvc.perform(get(PRODUCTS_ENDPOINT + "/" + TEST_PRODUCT_SKU))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/hal+json"))
                .andExpect(jsonPath("$.sku").value(TEST_PRODUCT_SKU))
                .andExpect(jsonPath("$._links.self.href", endsWith(PRODUCTS_ENDPOINT + "/" + TEST_PRODUCT_SKU)));
    }

    @Test
    void shouldResponseWith404IfProductDoesntExist() throws Exception {

        given(manageProductUseCase.findProduct(TEST_PRODUCT_SKU)).willReturn(null);

        this.mockMvc.perform(get(PRODUCTS_ENDPOINT + "/" + TEST_PRODUCT_SKU)).andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateProductSuccessfully() throws Exception {

        final Product draft = Product.builder().name(TEST_DRAFT_NAME).unitPrice(BigDecimal.ONE).build();
        final Product created = ProductBuilder.mockProduct();
        given(productWebMapper.mapToDomainObject(any())).willReturn(Optional.of(draft));
        given(productWebMapper.extractCategorySlug(any())).willReturn(ProductResourceBuilder.TEST_CATEGORY_SLUG);
        given(manageProductUseCase.createProduct(draft, ProductResourceBuilder.TEST_CATEGORY_SLUG)).willReturn(created);
        given(productWebMapper.mapToResource(created)).willReturn(Optional.of(mockProductDetailsResource()));

        this.mockMvc
                .perform(post(PRODUCTS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createJsonProductResource()))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(TEST_PRODUCT_SKU));

        verify(productMetrics).recordProductCreated();
    }

    @Test
    void shouldReturn400WhenProductResourceIsMissing() throws Exception {

        given(productWebMapper.mapToDomainObject(any())).willReturn(Optional.empty());

        this.mockMvc
                .perform(post(PRODUCTS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createJsonProductResource()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Product data is missing"));

        verify(manageProductUseCase, never()).createProduct(any(), anyString());
        verify(productMetrics, never()).recordProductCreated();
    }

    @Test
    void shouldReturn400WhenCategoryDoesNotExist() throws Exception {

        final Product draft = Product.builder().name(TEST_DRAFT_NAME).unitPrice(BigDecimal.ONE).build();
        given(productWebMapper.mapToDomainObject(any())).willReturn(Optional.of(draft));
        given(productWebMapper.extractCategorySlug(any())).willReturn("unknown");
        willThrow(new CategoryNotFoundException("unknown")).given(manageProductUseCase).createProduct(eq(draft), eq("unknown"));

        this.mockMvc
                .perform(post(PRODUCTS_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createJsonProductResource()))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(productMetrics, never()).recordProductCreated();
    }

    @Test
    void shouldUpdateProductSuccessfully() throws Exception {

        final Product update = Product.builder().name(TEST_DRAFT_NAME).unitPrice(BigDecimal.ONE).build();
        final Product updated = ProductBuilder.mockProduct();
        given(productWebMapper.mapToDomainObject(any())).willReturn(Optional.of(update));
        given(manageProductUseCase.updateProduct(TEST_PRODUCT_SKU, update)).willReturn(updated);
        given(productWebMapper.mapToResource(updated)).willReturn(Optional.of(mockProductDetailsResource()));

        this.mockMvc
                .perform(
                        put(PRODUCTS_ENDPOINT + "/" + TEST_PRODUCT_SKU).contentType(MediaType.APPLICATION_JSON)
                                .content(createJsonProductResource()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(TEST_PRODUCT_SKU));

        verify(productMetrics).recordProductUpdated();
    }

    @Test
    void shouldReturn400WhenUpdateProductResourceIsMissing() throws Exception {

        given(productWebMapper.mapToDomainObject(any())).willReturn(Optional.empty());

        this.mockMvc
                .perform(
                        put(PRODUCTS_ENDPOINT + "/" + TEST_PRODUCT_SKU).contentType(MediaType.APPLICATION_JSON)
                                .content(createJsonProductResource()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Product data is missing"));

        verify(manageProductUseCase, never()).updateProduct(anyString(), any());
        verify(productMetrics, never()).recordProductUpdated();
    }

    @Test
    void shouldThrowTechnicalProblemWhenMapToResourceReturnsEmpty() throws Exception {

        final Product product = ProductBuilder.mockProduct();
        given(manageProductUseCase.findProduct(TEST_PRODUCT_SKU)).willReturn(product);
        given(productWebMapper.mapToResource(product)).willReturn(Optional.empty());

        this.mockMvc.perform(get(PRODUCTS_ENDPOINT + "/" + TEST_PRODUCT_SKU))
                .andDo(print())
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldResponseWith404WhenUpdatingNonExistentProduct() throws Exception {

        final Product update = Product.builder().name(TEST_DRAFT_NAME).unitPrice(BigDecimal.ONE).build();
        given(productWebMapper.mapToDomainObject(any())).willReturn(Optional.of(update));
        given(manageProductUseCase.updateProduct(TEST_PRODUCT_SKU, update)).willReturn(null);

        this.mockMvc
                .perform(
                        put(PRODUCTS_ENDPOINT + "/" + TEST_PRODUCT_SKU).contentType(MediaType.APPLICATION_JSON)
                                .content(createJsonProductResource()))
                .andExpect(status().isNotFound());

        verify(productMetrics, never()).recordProductUpdated();
    }

    private ProductDetailsResource mockProductDetailsResource() {

        return ProductDetailsResource.builder()
                .sku(TEST_PRODUCT_SKU)
                .name(ProductResourceBuilder.TEST_PRODUCT_NAME)
                .description(ProductResourceBuilder.TEST_PRODUCT_DESCRIPTION)
                .categorySlug(ProductResourceBuilder.TEST_CATEGORY_SLUG)
                .unitPrice(ProductResourceBuilder.TEST_PRODUCT_UNIT_PRICE)
                .imageUrl(ProductResourceBuilder.TEST_PRODUCT_IMAGE_URL)
                .active(true)
                .build();
    }

    private String createJsonProductResource() throws Exception {

        final ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(ProductResourceBuilder.mockProductResource());
    }

}
