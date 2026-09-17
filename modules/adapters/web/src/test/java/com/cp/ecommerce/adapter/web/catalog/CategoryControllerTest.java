package com.cp.ecommerce.adapter.web.catalog;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.CategoryBuilder;
import com.cp.ecommerce.adapter.web.catalog.mapper.CategoryWebMapper;
import com.cp.ecommerce.adapter.web.utils.CategoryResourceBuilder;
import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.usecase.CreateCategoryUseCase;
import com.cp.ecommerce.domain.catalog.usecase.ListCategoriesUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test class checking category controller's behavior and category API response.
 */
@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    private static final String CATEGORIES_ENDPOINT = "/api/catalog/categories";

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient ListCategoriesUseCase listCategoriesUseCase;

    @MockitoBean
    private transient CreateCategoryUseCase createCategoryUseCase;

    @MockitoBean
    private transient CategoryWebMapper categoryWebMapper;

    @Test
    void shouldListCategories() throws Exception {

        final Category category = CategoryBuilder.mockCategory();
        given(listCategoriesUseCase.listCategories()).willReturn(List.of(category));
        given(categoryWebMapper.mapToResource(category))
                .willReturn(Optional.of(CategoryResourceBuilder.mockCategoryResource()));

        this.mockMvc.perform(get(CATEGORIES_ENDPOINT))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value(CategoryResourceBuilder.TEST_CATEGORY_NAME))
                .andExpect(jsonPath("$[0].slug").value(CategoryResourceBuilder.TEST_CATEGORY_SLUG));
    }

    @Test
    void shouldSkipUnmappableCategoriesWhenListing() throws Exception {

        final Category category = CategoryBuilder.mockCategory();
        given(listCategoriesUseCase.listCategories()).willReturn(List.of(category));
        given(categoryWebMapper.mapToResource(category)).willReturn(Optional.empty());

        this.mockMvc.perform(get(CATEGORIES_ENDPOINT))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldCreateCategory() throws Exception {

        final Category category = CategoryBuilder.mockCategory();
        given(categoryWebMapper.mapToDomainObject(any())).willReturn(Optional.of(category));
        given(createCategoryUseCase.createCategory(category)).willReturn(category);
        given(categoryWebMapper.mapToResource(category))
                .willReturn(Optional.of(CategoryResourceBuilder.mockCategoryResource()));

        this.mockMvc.perform(post(CATEGORIES_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createJsonResource()))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value(CategoryResourceBuilder.TEST_CATEGORY_NAME));
    }

    @Test
    void shouldReturn400WhenCategoryResourceIsMissing() throws Exception {

        given(categoryWebMapper.mapToDomainObject(any())).willReturn(Optional.empty());

        this.mockMvc.perform(post(CATEGORIES_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createJsonResource()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Category data is missing"));

        verify(createCategoryUseCase, never()).createCategory(any());
    }

    @Test
    void shouldThrowTechnicalProblemWhenCreatedCategoryMapToResourceReturnsEmpty() throws Exception {

        final Category category = CategoryBuilder.mockCategory();
        given(categoryWebMapper.mapToDomainObject(any())).willReturn(Optional.of(category));
        given(createCategoryUseCase.createCategory(category)).willReturn(category);
        given(categoryWebMapper.mapToResource(category)).willReturn(Optional.empty());

        this.mockMvc.perform(post(CATEGORIES_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createJsonResource()))
                .andDo(print())
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldReturn400WhenCategoryFailsDomainValidation() throws Exception {

        final Category invalidCategory = Category.builder().name("").slug("").build();
        given(categoryWebMapper.mapToDomainObject(any())).willReturn(Optional.of(invalidCategory));

        this.mockMvc.perform(post(CATEGORIES_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(createJsonResource()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(createCategoryUseCase, never()).createCategory(any());
    }

    private String createJsonResource() throws Exception {

        final ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(CategoryResourceBuilder.mockCategoryResource());
    }

}
