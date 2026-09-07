package com.cp.ecommerce.adapter.ai.recommendation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.recommendation.CustomerReviewProfile;
import com.cp.ecommerce.domain.recommendation.ProductRecommendation;
import com.cp.ecommerce.domain.recommendation.ProductRecommendations;
import com.cp.ecommerce.domain.recommendation.PurchasedProductProfile;
import com.cp.ecommerce.domain.recommendation.RecommendationCandidateProduct;
import com.cp.ecommerce.domain.recommendation.RecommendationRequest;
import com.cp.ecommerce.domain.recommendation.port.outgoing.GenerateProductRecommendationsOutPort;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Generates short, structured product recommendations using the existing Ollama-backed Spring AI chat model.
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "true")
public class ProductRecommendationsAdapter implements GenerateProductRecommendationsOutPort {

    private static final String RESILIENCE_INSTANCE_NAME = "recommendProducts";

    private static final int MAX_RECOMMENDATIONS = 5;

    private static final String SYSTEM_PROMPT = """
            You recommend products for an e-commerce customer. Use only the provided purchase history, review history, and             candidate catalog products. Recommend 3 to 5 products the customer has not already bought. Prefer similar or             complementary items based on purchased and positively reviewed products, but avoid products clearly mismatched with             negative review signals. Respond only with structured data using candidate SKUs exactly as provided, and keep each             reason to one short sentence.""";

    private final ChatClient chatClient;

    private final ResilientExecutor resilientExecutor;

    public ProductRecommendationsAdapter(
            final ChatClient.Builder chatClientBuilder,
            final ResilientExecutor resilientExecutor) {

        this.chatClient = chatClientBuilder.build();
        this.resilientExecutor = resilientExecutor;
    }

    @Override
    public ProductRecommendations recommend(
            final RecommendationRequest request,
            final List<PurchasedProductProfile> purchasedProducts,
            final List<CustomerReviewProfile> reviews,
            final List<RecommendationCandidateProduct> candidates) {

        try {
            return resilientExecutor.callResilient(
                    RESILIENCE_INSTANCE_NAME,
                    () -> recommendWithModel(request, purchasedProducts, reviews, candidates));
        } catch (Exception exception) {
            log.warn(
                    "Could not generate personalized recommendations via Ollama for customer {}.",
                    request.getCustomerEmail(),
                    exception);
            return ProductRecommendations.unavailable();
        }
    }

    private ProductRecommendations recommendWithModel(
            final RecommendationRequest request,
            final List<PurchasedProductProfile> purchasedProducts,
            final List<CustomerReviewProfile> reviews,
            final List<RecommendationCandidateProduct> candidates) {

        final ProductRecommendationsResponse response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(buildUserPrompt(request, purchasedProducts, reviews, candidates))
                .call()
                .entity(ProductRecommendationsResponse.class);

        return ProductRecommendations.builder()
                .recommendations(normalizeRecommendations(response, purchasedProducts, candidates))
                .assistantAvailable(true)
                .build();
    }

    private List<ProductRecommendation> normalizeRecommendations(
            final ProductRecommendationsResponse response,
            final List<PurchasedProductProfile> purchasedProducts,
            final List<RecommendationCandidateProduct> candidates) {

        final Map<String, RecommendationCandidateProduct> candidateBySku = candidates.stream()
                .collect(
                        Collectors.toMap(
                                RecommendationCandidateProduct::getSku,
                                candidate -> candidate,
                                (left, right) -> left,
                                LinkedHashMap::new));
        final Set<String> purchasedSkus = purchasedProducts.stream()
                .map(PurchasedProductProfile::getSku)
                .collect(Collectors.toSet());
        if (response == null || response.recommendations() == null) {
            return List.of();
        }
        return response.recommendations()
                .stream()
                .filter(item -> item != null && StringUtils.hasText(item.sku()))
                .map(item -> toRecommendation(item, candidateBySku.get(item.sku().trim())))
                .filter(item -> item != null && !purchasedSkus.contains(item.getSku()))
                .collect(
                        Collectors
                                .toMap(ProductRecommendation::getSku, item -> item, (left, right) -> left, LinkedHashMap::new))
                .values()
                .stream()
                .limit(MAX_RECOMMENDATIONS)
                .toList();
    }

    private ProductRecommendation toRecommendation(
            final ProductRecommendationResponse response,
            final RecommendationCandidateProduct candidate) {

        if (candidate == null || !StringUtils.hasText(response.reason())) {
            return null;
        }
        final String productName = StringUtils.hasText(response.productName())
                ? response.productName().trim()
                : candidate.getProductName();
        return ProductRecommendation.builder()
                .sku(candidate.getSku())
                .productName(productName)
                .reason(response.reason().trim())
                .build();
    }

    private String buildUserPrompt(
            final RecommendationRequest request,
            final List<PurchasedProductProfile> purchasedProducts,
            final List<CustomerReviewProfile> reviews,
            final List<RecommendationCandidateProduct> candidates) {

        return """
                Customer email: %s

                Purchased products:
                %s

                Review history:
                %s

                Candidate products you may choose from (and only from):
                %s

                Return 3 to 5 recommendations.
                """.formatted(
                request.getCustomerEmail(),
                formatPurchasedProducts(purchasedProducts),
                formatReviews(reviews),
                formatCandidates(candidates));
    }

    private String formatPurchasedProducts(final List<PurchasedProductProfile> purchasedProducts) {

        if (purchasedProducts.isEmpty()) {
            return "- none";
        }
        return purchasedProducts.stream()
                .map(
                        product -> String.format(
                                Locale.ROOT,
                                "- %s | %s | qty=%d",
                                product.getSku(),
                                product.getProductName(),
                                product.getTotalQuantity()))
                .collect(Collectors.joining("\n"));
    }

    private String formatReviews(final List<CustomerReviewProfile> reviews) {

        if (reviews.isEmpty()) {
            return "- none";
        }
        return reviews.stream()
                .map(
                        review -> String.format(
                                Locale.ROOT,
                                "- %s | rating=%d/5 | %s",
                                review.getSku(),
                                review.getRating(),
                                sanitize(review.getComment())))
                .collect(Collectors.joining("\n"));
    }

    private String formatCandidates(final List<RecommendationCandidateProduct> candidates) {

        return candidates.stream()
                .map(
                        candidate -> String.format(
                                Locale.ROOT,
                                "- %s | %s | category=%s | %s",
                                candidate.getSku(),
                                candidate.getProductName(),
                                sanitize(candidate.getCategoryName()),
                                sanitize(candidate.getDescription())))
                .collect(Collectors.joining("\n"));
    }

    private String sanitize(final String value) {

        return StringUtils.hasText(value) ? value.trim() : "n/a";
    }

}
