package com.cp.ecommerce.domain.recommendation.usecase;

import java.util.List;
import java.util.Set;

import com.cp.ecommerce.domain.recommendation.CustomerReviewProfile;
import com.cp.ecommerce.domain.recommendation.ProductRecommendation;
import com.cp.ecommerce.domain.recommendation.ProductRecommendations;
import com.cp.ecommerce.domain.recommendation.PurchasedProductProfile;
import com.cp.ecommerce.domain.recommendation.RecommendationCandidateProduct;
import com.cp.ecommerce.domain.recommendation.RecommendationRequest;
import com.cp.ecommerce.domain.recommendation.port.outgoing.FindCustomerPurchaseHistoryOutPort;
import com.cp.ecommerce.domain.recommendation.port.outgoing.FindCustomerReviewHistoryOutPort;
import com.cp.ecommerce.domain.recommendation.port.outgoing.FindRecommendationCandidateProductsOutPort;
import com.cp.ecommerce.domain.recommendation.port.outgoing.GenerateProductRecommendationsOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RecommendProductsUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class RecommendProductsUseCaseTest {

    @Mock
    private transient FindCustomerPurchaseHistoryOutPort findCustomerPurchaseHistoryOutPort;

    @Mock
    private transient FindCustomerReviewHistoryOutPort findCustomerReviewHistoryOutPort;

    @Mock
    private transient FindRecommendationCandidateProductsOutPort findRecommendationCandidateProductsOutPort;

    @Mock
    private transient GenerateProductRecommendationsOutPort generateProductRecommendationsOutPort;

    @InjectMocks
    private transient RecommendProductsUseCase useCase;

    @Test
    void shouldLoadHistoryAndDelegateToAiAdapter() {

        final RecommendationRequest request = RecommendationRequest.builder().customerEmail("john.doe@test.com").build();
        final List<PurchasedProductProfile> purchases = List
                .of(PurchasedProductProfile.builder().sku("SKU-1").productName("Mouse").totalQuantity(2).build());
        final List<CustomerReviewProfile> reviews = List
                .of(CustomerReviewProfile.builder().sku("SKU-1").rating(5).comment("Great").build());
        final List<RecommendationCandidateProduct> candidates = List.of(
                RecommendationCandidateProduct.builder()
                        .sku("SKU-2")
                        .productName("Keyboard")
                        .description("desc")
                        .categoryName("Electronics")
                        .build());
        final ProductRecommendations expected = ProductRecommendations.builder()
                .recommendations(
                        List.of(
                                ProductRecommendation.builder()
                                        .sku("SKU-2")
                                        .productName("Keyboard")
                                        .reason("Complements the mouse.")
                                        .build()))
                .assistantAvailable(true)
                .build();
        when(findCustomerPurchaseHistoryOutPort.findPurchasedProducts("john.doe@test.com")).thenReturn(purchases);
        when(findCustomerReviewHistoryOutPort.findReviews("john.doe@test.com")).thenReturn(reviews);
        when(findRecommendationCandidateProductsOutPort.findCandidates(Set.of("SKU-1"))).thenReturn(candidates);
        when(generateProductRecommendationsOutPort.recommend(request, purchases, reviews, candidates)).thenReturn(expected);

        final ProductRecommendations actual = useCase.recommendProducts(request);

        assertThat(actual).isEqualTo(expected);
        verify(generateProductRecommendationsOutPort).recommend(request, purchases, reviews, candidates);
    }

    @Test
    void shouldReturnEmptySuccessfulResponseWhenNoCandidatesExist() {

        final RecommendationRequest request = RecommendationRequest.builder().customerEmail("john.doe@test.com").build();
        when(findCustomerPurchaseHistoryOutPort.findPurchasedProducts(any())).thenReturn(
                List.of(PurchasedProductProfile.builder().sku("SKU-1").productName("Mouse").totalQuantity(2).build()));
        when(findCustomerReviewHistoryOutPort.findReviews(any())).thenReturn(List.of());
        when(findRecommendationCandidateProductsOutPort.findCandidates(Set.of("SKU-1"))).thenReturn(List.of());

        final ProductRecommendations actual = useCase.recommendProducts(request);

        assertThat(actual.isAssistantAvailable()).isTrue();
        assertThat(actual.getRecommendations()).isEmpty();
        verify(generateProductRecommendationsOutPort, never()).recommend(any(), any(), any(), any());
    }

}
