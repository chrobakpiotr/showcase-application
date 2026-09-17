package com.cp.ecommerce.adapter.persistence.order.analytics.mapper;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.order.analytics.OpsDigestEntity;
import com.cp.ecommerce.domain.order.OpsDigest;
import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OpsDigestPersistenceMapper} mapper test.
 */
class OpsDigestPersistenceMapperTest {

    private static final String NARRATIVE = "Busy day, one complaint to review.";

    private final transient OpsDigestPersistenceMapper opsDigestPersistenceMapper = new OpsDigestPersistenceMapper();

    @Test
    void shouldMapDomainToEntityFlatteningCountsByCategory() {

        final Date generatedDate = new Date();
        final OpsDigest opsDigest = OpsDigest.builder()
                .generatedDate(generatedDate)
                .ordersPlacedLastDay(9L)
                .remarksClassificationSummary(
                        new RemarksClassificationSummary(
                                Map.of(
                                        RemarksTriageCategory.STANDARD,
                                        6L,
                                        RemarksTriageCategory.URGENT,
                                        2L,
                                        RemarksTriageCategory.COMPLAINT,
                                        1L,
                                        RemarksTriageCategory.SUSPICIOUS,
                                        0L)))
                .narrative(NARRATIVE)
                .build();

        final Optional<OpsDigestEntity> result = opsDigestPersistenceMapper.mapToEntity(opsDigest);

        assertThat(result).isPresent();
        assertThat(result.get().getGeneratedDate()).isEqualTo(generatedDate);
        assertThat(result.get().getOrdersPlacedLastDay()).isEqualTo(9L);
        assertThat(result.get().getStandardCount()).isEqualTo(6L);
        assertThat(result.get().getUrgentCount()).isEqualTo(2L);
        assertThat(result.get().getComplaintCount()).isEqualTo(1L);
        assertThat(result.get().getSuspiciousCount()).isEqualTo(0L);
        assertThat(result.get().getNarrative()).isEqualTo(NARRATIVE);
    }

    @Test
    void shouldMapEntityToDomainRebuildingCountsByCategory() {

        final Date generatedDate = new Date();
        final OpsDigestEntity entity = OpsDigestEntity.builder()
                .id(1L)
                .generatedDate(generatedDate)
                .ordersPlacedLastDay(9L)
                .standardCount(6L)
                .urgentCount(2L)
                .complaintCount(1L)
                .suspiciousCount(0L)
                .narrative(NARRATIVE)
                .build();

        final Optional<OpsDigest> result = opsDigestPersistenceMapper.mapToDomainObject(entity);

        assertThat(result).isPresent();
        assertThat(result.get().getGeneratedDate()).isEqualTo(generatedDate);
        assertThat(result.get().getOrdersPlacedLastDay()).isEqualTo(9L);
        assertThat(result.get().getNarrative()).isEqualTo(NARRATIVE);
        assertThat(result.get().getRemarksClassificationSummary().countsByCategory())
                .containsEntry(RemarksTriageCategory.STANDARD, 6L)
                .containsEntry(RemarksTriageCategory.URGENT, 2L)
                .containsEntry(RemarksTriageCategory.COMPLAINT, 1L)
                .containsEntry(RemarksTriageCategory.SUSPICIOUS, 0L);
    }

    @Test
    void shouldMapNullEntityToEmptyOptional() {

        final Optional<OpsDigest> result = opsDigestPersistenceMapper.mapToDomainObject(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldMapNullDomainObjectToEmptyOptional() {

        final Optional<OpsDigestEntity> result = opsDigestPersistenceMapper.mapToEntity(null);
        assertTrue(result.isEmpty());
    }

}
