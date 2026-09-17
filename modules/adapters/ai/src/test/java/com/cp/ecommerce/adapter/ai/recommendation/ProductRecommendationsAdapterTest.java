package com.cp.ecommerce.adapter.ai.recommendation;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.recommendation.CustomerReviewProfile;
import com.cp.ecommerce.domain.recommendation.ProductRecommendation;
import com.cp.ecommerce.domain.recommendation.ProductRecommendations;
import com.cp.ecommerce.domain.recommendation.PurchasedProductProfile;
import com.cp.ecommerce.domain.recommendation.RecommendationCandidateProduct;
import com.cp.ecommerce.domain.recommendation.RecommendationRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ProductRecommendationsAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class ProductRecommendationsAdapterTest {

    private static final String CUSTOMER_EMAIL = "john.doe@test.com";

    private static final String KEYBOARD_SKU = "SKU-2";

    private static final String KEYBOARD_NAME = "Keyboard";

    private static final String DEFAULT_DESCRIPTION = "desc";

    private static final String ELECTRONICS_CATEGORY = "Electronics";

    @Mock
    transient ChatModel chatModel;

    @Mock
    transient ResilientExecutor resilientExecutor;

    @Test
    void shouldMapStructuredRecommendationsFromTheModel() {

        respondWith("""
                {"recommendations":[
                  {"sku":"SKU-2","productName":"Keyboard","reason":"Pairs well with the mouse you bought."},
                  {"sku":"SKU-3","productName":"Desk Mat","reason":"Complements your desk setup."}
                ]}""");
        runResilientActionEagerly();
        final ProductRecommendationsAdapter adapter = newAdapter();

        final ProductRecommendations result = adapter.recommend(
                RecommendationRequest.builder().customerEmail(CUSTOMER_EMAIL).build(),
                List.of(PurchasedProductProfile.builder().sku("SKU-1").productName("Mouse").totalQuantity(2).build()),
                List.of(CustomerReviewProfile.builder().sku("SKU-1").rating(5).comment("Great").build()),
                List.of(
                        RecommendationCandidateProduct.builder()
                                .sku(KEYBOARD_SKU)
                                .productName(KEYBOARD_NAME)
                                .description(DEFAULT_DESCRIPTION)
                                .categoryName(ELECTRONICS_CATEGORY)
                                .build(),
                        RecommendationCandidateProduct.builder()
                                .sku("SKU-3")
                                .productName("Desk Mat")
                                .description(DEFAULT_DESCRIPTION)
                                .categoryName("Accessories")
                                .build()));

        assertThat(result.isAssistantAvailable()).isTrue();
        assertThat(result.getRecommendations()).hasSize(2);
        assertThat(result.getRecommendations().getFirst().getSku()).isEqualTo(KEYBOARD_SKU);
    }

    @Test
    void shouldDropRecommendationsForUnknownOrPurchasedSkus() {

        respondWith("""
                {"recommendations":[
                  {"sku":"SKU-1","productName":"Mouse","reason":"Already bought."},
                  {"sku":"SKU-9","productName":"Ghost","reason":"Unknown."},
                  {"sku":"SKU-2","productName":"Keyboard","reason":"Good match."}
                ]}""");
        runResilientActionEagerly();
        final ProductRecommendationsAdapter adapter = newAdapter();

        final ProductRecommendations result = adapter.recommend(
                RecommendationRequest.builder().customerEmail(CUSTOMER_EMAIL).build(),
                List.of(PurchasedProductProfile.builder().sku("SKU-1").productName("Mouse").totalQuantity(2).build()),
                List.of(),
                List.of(
                        RecommendationCandidateProduct.builder()
                                .sku(KEYBOARD_SKU)
                                .productName(KEYBOARD_NAME)
                                .description(DEFAULT_DESCRIPTION)
                                .categoryName(ELECTRONICS_CATEGORY)
                                .build()));

        assertThat(result.getRecommendations()).singleElement()
                .satisfies(item -> assertThat(item.getSku()).isEqualTo(KEYBOARD_SKU));
    }

    @Test
    void shouldReturnUnavailableFallbackWhenResilienceFails() throws Exception {

        when(resilientExecutor.callResilient(anyString(), any())).thenThrow(new IllegalStateException("circuit open"));
        final ProductRecommendationsAdapter adapter = newAdapter();

        final ProductRecommendations result = adapter.recommend(
                RecommendationRequest.builder().customerEmail(CUSTOMER_EMAIL).build(),
                List.of(),
                List.of(),
                List.of(
                        RecommendationCandidateProduct.builder()
                                .sku(KEYBOARD_SKU)
                                .productName(KEYBOARD_NAME)
                                .description(DEFAULT_DESCRIPTION)
                                .categoryName(ELECTRONICS_CATEGORY)
                                .build()));

        assertThat(result.isAssistantAvailable()).isFalse();
        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void shouldReturnEmptyRecommendationsWhenModelResponseIsMissing() {

        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(Collections.singletonList(new Generation(new AssistantMessage("{}")))));
        runResilientActionEagerly();
        final ProductRecommendationsAdapter adapter = newAdapter();

        final ProductRecommendations result = adapter.recommend(
                RecommendationRequest.builder().customerEmail(CUSTOMER_EMAIL).build(),
                List.of(),
                List.of(),
                List.of(
                        RecommendationCandidateProduct.builder()
                                .sku(KEYBOARD_SKU)
                                .productName(KEYBOARD_NAME)
                                .description(DEFAULT_DESCRIPTION)
                                .categoryName(ELECTRONICS_CATEGORY)
                                .build()));

        assertThat(result.isAssistantAvailable()).isTrue();
        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void shouldIgnoreInvalidRowsAndFallbackNamesWhenModelReturnsPartialData() {

        respondWith("""
                {"recommendations":[
                  null,
                  {"sku":" ","productName":"Ignored","reason":"Missing sku."},
                  {"sku":"SKU-2","productName":" ","reason":"  Complements your setup.  "},
                  {"sku":"SKU-3","productName":"Desk Mat","reason":" "},
                  {"sku":"SKU-4","productName":"Stand","reason":"Great match."}
                ]}""");
        runResilientActionEagerly();
        final ProductRecommendationsAdapter adapter = newAdapter();

        final ProductRecommendations result = adapter.recommend(
                RecommendationRequest.builder().customerEmail(CUSTOMER_EMAIL).build(),
                List.of(),
                List.of(),
                List.of(
                        RecommendationCandidateProduct.builder()
                                .sku(KEYBOARD_SKU)
                                .productName(KEYBOARD_NAME)
                                .description(DEFAULT_DESCRIPTION)
                                .categoryName(ELECTRONICS_CATEGORY)
                                .build(),
                        RecommendationCandidateProduct.builder()
                                .sku("SKU-3")
                                .productName("Desk Mat")
                                .description("")
                                .categoryName(null)
                                .build()));

        assertThat(result.getRecommendations()).singleElement().satisfies(item -> {
            assertThat(item.getSku()).isEqualTo(KEYBOARD_SKU);
            assertThat(item.getProductName()).isEqualTo("Keyboard");
            assertThat(item.getReason()).isEqualTo("Complements your setup.");
        });
    }

    @Test
    void shouldHandleNullResponseAndDuplicateSkusWhenNormalizingRecommendations() {

        final ProductRecommendationsAdapter adapter = newAdapter();
        final List<RecommendationCandidateProduct> duplicateCandidates = List.of(
                RecommendationCandidateProduct.builder()
                        .sku(KEYBOARD_SKU)
                        .productName(KEYBOARD_NAME)
                        .description(DEFAULT_DESCRIPTION)
                        .categoryName(ELECTRONICS_CATEGORY)
                        .build(),
                RecommendationCandidateProduct.builder()
                        .sku(KEYBOARD_SKU)
                        .productName("Keyboard Duplicate")
                        .description(DEFAULT_DESCRIPTION)
                        .categoryName(ELECTRONICS_CATEGORY)
                        .build());

        final List<ProductRecommendation> emptyResult = ReflectionTestUtils
                .invokeMethod(adapter, "normalizeRecommendations", null, List.of(), duplicateCandidates);
        final List<ProductRecommendation> filteredPurchasedResult = ReflectionTestUtils.invokeMethod(
                adapter,
                "normalizeRecommendations",
                new ProductRecommendationsResponse(
                        List.of(new ProductRecommendationResponse(KEYBOARD_SKU, KEYBOARD_NAME, "Useful."))),
                List.of(PurchasedProductProfile.builder().sku(KEYBOARD_SKU).productName("Keyboard").totalQuantity(1).build()),
                duplicateCandidates);

        assertThat(emptyResult).isEmpty();
        assertThat(filteredPurchasedResult).isEmpty();
    }

    @Test
    void shouldKeepFirstRecommendationWhenModelReturnsDuplicateSkus() {

        respondWith("""
                {"recommendations":[
                  {"sku":"SKU-2","productName":"Keyboard","reason":"First reason."},
                  {"sku":"SKU-2","productName":"Keyboard","reason":"Second reason."}
                ]}""");
        runResilientActionEagerly();
        final ProductRecommendationsAdapter adapter = newAdapter();

        final ProductRecommendations result = adapter.recommend(
                RecommendationRequest.builder().customerEmail(CUSTOMER_EMAIL).build(),
                List.of(),
                List.of(),
                List.of(
                        RecommendationCandidateProduct.builder()
                                .sku(KEYBOARD_SKU)
                                .productName(KEYBOARD_NAME)
                                .description(DEFAULT_DESCRIPTION)
                                .categoryName(ELECTRONICS_CATEGORY)
                                .build()));

        assertThat(result.getRecommendations()).singleElement()
                .satisfies(item -> assertThat(item.getReason()).isEqualTo("First reason."));
    }

    private ProductRecommendationsAdapter newAdapter() {

        return new ProductRecommendationsAdapter(ChatClient.builder(chatModel), resilientExecutor);
    }

    private void respondWith(final String content) {

        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(Collections.singletonList(new Generation(new AssistantMessage(content)))));
    }

    @SuppressWarnings("unchecked")
    private void runResilientActionEagerly() {

        try {
            when(resilientExecutor.callResilient(anyString(), any())).thenAnswer(invocation -> {
                final Callable<ProductRecommendations> action = invocation.getArgument(1);
                return action.call();
            });
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

}
