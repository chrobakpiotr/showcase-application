package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.order.analytics.mapper.OpsDigestPersistenceMapper;
import com.cp.ecommerce.domain.order.OpsDigest;
import com.cp.ecommerce.domain.order.RemarksClassificationSummary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Test class for {@link GetLatestOpsDigestAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class GetLatestOpsDigestAdapterTest {

    @InjectMocks
    private transient GetLatestOpsDigestAdapter getLatestOpsDigestAdapter;

    @Mock
    private transient OpsDigestEntityRepository opsDigestEntityRepository;

    @Mock
    private transient OpsDigestPersistenceMapper opsDigestPersistenceMapper;

    @Test
    void shouldMapAndReturnLatestDigest() {

        final OpsDigestEntity entity = OpsDigestEntity.builder().narrative("All quiet.").build();
        final OpsDigest domain = OpsDigest.builder()
                .generatedDate(new Date())
                .ordersPlacedLastDay(4L)
                .remarksClassificationSummary(new RemarksClassificationSummary(Map.of()))
                .narrative("All quiet.")
                .build();
        given(opsDigestEntityRepository.findFirstByOrderByGeneratedDateDesc()).willReturn(Optional.of(entity));
        given(opsDigestPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.of(domain));

        final Optional<OpsDigest> actual = getLatestOpsDigestAdapter.findLatest();

        assertThat(actual).contains(domain);
    }

    @Test
    void shouldReturnEmptyWhenNoDigestPersistedYet() {

        given(opsDigestEntityRepository.findFirstByOrderByGeneratedDateDesc()).willReturn(Optional.empty());

        final Optional<OpsDigest> actual = getLatestOpsDigestAdapter.findLatest();

        assertThat(actual).isEmpty();
    }

}
