package com.cp.ecommerce.domain.order.usecase;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

import com.cp.ecommerce.domain.order.OpsDigest;
import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.port.outgoing.GetLatestOpsDigestOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GetLatestOpsDigestUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class GetLatestOpsDigestUseCaseTest {

    @Mock
    private transient GetLatestOpsDigestOutPort getLatestOpsDigestOutPort;

    @InjectMocks
    private transient GetLatestOpsDigestUseCase getLatestOpsDigestUseCase;

    @Test
    void shouldDelegateToOutPort() {

        final OpsDigest expected = OpsDigest.builder()
                .generatedDate(new Date())
                .ordersPlacedLastDay(3L)
                .remarksClassificationSummary(new RemarksClassificationSummary(Map.of()))
                .narrative("All quiet.")
                .build();
        when(getLatestOpsDigestOutPort.findLatest()).thenReturn(Optional.of(expected));

        final Optional<OpsDigest> actual = getLatestOpsDigestUseCase.getLatestDigest();

        assertThat(actual).contains(expected);
    }

    @Test
    void shouldReturnEmptyWhenNoDigestGeneratedYet() {

        when(getLatestOpsDigestOutPort.findLatest()).thenReturn(Optional.empty());

        final Optional<OpsDigest> actual = getLatestOpsDigestUseCase.getLatestDigest();

        assertThat(actual).isEmpty();
    }

}
