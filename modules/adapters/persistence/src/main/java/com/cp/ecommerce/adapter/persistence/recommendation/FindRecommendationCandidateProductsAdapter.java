package com.cp.ecommerce.adapter.persistence.recommendation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntity;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntityRepository;
import com.cp.ecommerce.adapter.persistence.catalog.mapper.ProductPersistenceMapper;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.recommendation.RecommendationCandidateProduct;
import com.cp.ecommerce.domain.recommendation.port.outgoing.FindRecommendationCandidateProductsOutPort;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import lombok.RequiredArgsConstructor;

/**
 * Reads a small shortlist of active catalog products eligible to be recommended.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindRecommendationCandidateProductsAdapter implements FindRecommendationCandidateProductsOutPort {

    private static final int CANDIDATE_FETCH_SIZE = 24;

    private static final int CANDIDATE_RETURN_SIZE = 12;

    private final ProductEntityRepository productEntityRepository;

    private final ProductPersistenceMapper productPersistenceMapper;

    @Override
    public List<RecommendationCandidateProduct> findCandidates(final Set<String> excludedSkus) {

        return productEntityRepository
                .findAllByActive(true, PageRequest.of(0, CANDIDATE_FETCH_SIZE, Sort.by(Sort.Direction.ASC, "name")))
                .getContent()
                .stream()
                .filter(product -> !excludedSkus.contains(product.getSku()))
                .map(this::toCandidate)
                .flatMap(Optional::stream)
                .limit(CANDIDATE_RETURN_SIZE)
                .toList();
    }

    private Optional<RecommendationCandidateProduct> toCandidate(final ProductEntity entity) {

        return productPersistenceMapper.mapToDomainObject(entity).map(this::toCandidate);
    }

    private RecommendationCandidateProduct toCandidate(final Product product) {

        return RecommendationCandidateProduct.builder()
                .sku(product.getSku())
                .productName(product.getName())
                .description(product.getDescription())
                .categoryName(product.getCategory() == null ? null : product.getCategory().getName())
                .build();
    }

}
