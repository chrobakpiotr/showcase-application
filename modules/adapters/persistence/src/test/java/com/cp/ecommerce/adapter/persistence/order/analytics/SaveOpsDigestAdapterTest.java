package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.order.analytics.mapper.OpsDigestPersistenceMapper;
import com.cp.ecommerce.domain.order.OpsDigest;
import com.cp.ecommerce.domain.order.RemarksClassificationSummary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Test class for {@link SaveOpsDigestAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveOpsDigestAdapterTest {

    @InjectMocks
    private transient SaveOpsDigestAdapter saveOpsDigestAdapter;

    @Mock
    private transient OpsDigestEntityRepository opsDigestEntityRepository;

    @Mock
    private transient OpsDigestPersistenceMapper opsDigestPersistenceMapper;

    @Test
    void shouldMapAndSaveDigest() {

        final OpsDigest opsDigest = OpsDigest.builder()
                .generatedDate(new Date())
                .ordersPlacedLastDay(4L)
                .remarksClassificationSummary(new RemarksClassificationSummary(Map.of()))
                .narrative("All quiet.")
                .build();
        final OpsDigestEntity entity = OpsDigestEntity.builder().narrative("All quiet.").build();
        given(opsDigestPersistenceMapper.mapToEntity(opsDigest)).willReturn(Optional.of(entity));

        saveOpsDigestAdapter.save(opsDigest);

        final ArgumentCaptor<OpsDigestEntity> captor = ArgumentCaptor.forClass(OpsDigestEntity.class);
        then(opsDigestEntityRepository).should().save(captor.capture());
        assertThat(captor.getValue()).isSameAs(entity);
    }

    @Test
    void shouldNotSaveWhenMappingFails() {

        final OpsDigest opsDigest = OpsDigest.builder()
                .generatedDate(new Date())
                .ordersPlacedLastDay(4L)
                .remarksClassificationSummary(new RemarksClassificationSummary(Map.of()))
                .narrative("All quiet.")
                .build();
        given(opsDigestPersistenceMapper.mapToEntity(opsDigest)).willReturn(Optional.empty());

        saveOpsDigestAdapter.save(opsDigest);

        then(opsDigestEntityRepository).should(never()).save(any());
    }

}
