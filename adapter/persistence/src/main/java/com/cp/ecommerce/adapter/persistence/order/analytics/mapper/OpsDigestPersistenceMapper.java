package com.cp.ecommerce.adapter.persistence.order.analytics.mapper;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.order.analytics.OpsDigestEntity;
import com.cp.ecommerce.domain.order.OpsDigest;
import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;

import org.springframework.stereotype.Component;

import static java.util.Optional.ofNullable;

/**
 * Mapper responsible for changing {@link OpsDigest} object into/from entity object (see ADR 0022). The remarks-classification
 * breakdown is flattened into four fixed columns rather than a single JSON/serialized column, since
 * {@link RemarksTriageCategory} is a small, closed enum unlikely to ever grow - keeping the schema simple, queryable SQL rather
 * than introducing a JSON column type for a four-entry map.
 */
@Component
public class OpsDigestPersistenceMapper implements PersistenceMapper<OpsDigest, OpsDigestEntity> {

    @Override
    public Optional<OpsDigestEntity> mapToEntity(final OpsDigest opsDigest) {

        return ofNullable(opsDigest).map(domain -> {
            final Map<RemarksTriageCategory, Long> countsByCategory = domain.getRemarksClassificationSummary()
                    .countsByCategory();
            return OpsDigestEntity.builder()
                    .generatedDate(domain.getGeneratedDate())
                    .ordersPlacedLastDay(domain.getOrdersPlacedLastDay())
                    .standardCount(countsByCategory.getOrDefault(RemarksTriageCategory.STANDARD, 0L))
                    .urgentCount(countsByCategory.getOrDefault(RemarksTriageCategory.URGENT, 0L))
                    .complaintCount(countsByCategory.getOrDefault(RemarksTriageCategory.COMPLAINT, 0L))
                    .suspiciousCount(countsByCategory.getOrDefault(RemarksTriageCategory.SUSPICIOUS, 0L))
                    .narrative(domain.getNarrative())
                    .build();
        });
    }

    @Override
    public Optional<OpsDigest> mapToDomainObject(final OpsDigestEntity entity) {

        return ofNullable(entity).map(
                e -> OpsDigest.builder()
                        .generatedDate(e.getGeneratedDate())
                        .ordersPlacedLastDay(e.getOrdersPlacedLastDay())
                        .remarksClassificationSummary(new RemarksClassificationSummary(toCountsByCategory(e)))
                        .narrative(e.getNarrative())
                        .build());
    }

    private Map<RemarksTriageCategory, Long> toCountsByCategory(final OpsDigestEntity entity) {

        final Map<RemarksTriageCategory, Long> countsByCategory = new EnumMap<>(RemarksTriageCategory.class);
        countsByCategory.put(RemarksTriageCategory.STANDARD, entity.getStandardCount());
        countsByCategory.put(RemarksTriageCategory.URGENT, entity.getUrgentCount());
        countsByCategory.put(RemarksTriageCategory.COMPLAINT, entity.getComplaintCount());
        countsByCategory.put(RemarksTriageCategory.SUSPICIOUS, entity.getSuspiciousCount());
        return countsByCategory;
    }

}
