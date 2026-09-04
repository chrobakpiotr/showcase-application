package com.cp.ecommerce.adapter.ai.order;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.order.DuplicateOrderCheckResult;
import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.mockCustomer;

/**
 * Unit tests for {@link DuplicateOrderDetectorAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class DuplicateOrderDetectorAdapterTest {

    private static final String ORD_1 = "ORD-1";

    private static final String ORD_2 = "ORD-2";

    private static final String ORD_3 = "ORD-3";

    private static final String UNRELATED_REMARK = "unrelated remark";

    private static final String DELIVER_TOMORROW = "please deliver tomorrow";

    @Mock
    transient EmbeddingModel embeddingModel;

    @Mock
    transient ResilientExecutor resilientExecutor;

    @Test
    void shouldFlagBlankRemarksMatchWithoutCallingModel() {

        final Order order = orderWithRemarks(ORD_1, "");
        final Order candidate = orderWithRemarks(ORD_2, "  ");
        final DuplicateOrderDetectorAdapter adapter = newAdapter();

        final DuplicateOrderCheckResult result = adapter.check(order, List.of(candidate));

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getMatchedOrderNumber()).isEqualTo(ORD_2);
        assertThat(result.getSimilarityScore()).isEqualTo(1.0);
        verifyNoInteractions(embeddingModel, resilientExecutor);
    }

    @Test
    void shouldReturnNoneWithoutCallingModelWhenBlankRemarksHaveNoBlankCandidate() {

        final Order order = orderWithRemarks(ORD_1, null);
        final Order candidate = orderWithRemarks(ORD_2, DELIVER_TOMORROW);
        final DuplicateOrderDetectorAdapter adapter = newAdapter();

        final DuplicateOrderCheckResult result = adapter.check(order, List.of(candidate));

        assertThat(result).isEqualTo(DuplicateOrderCheckResult.none());
        verifyNoInteractions(embeddingModel, resilientExecutor);
    }

    @Test
    void shouldReturnNoneWithoutCallingModelWhenNoCandidateHasComparableRemarks() {

        final Order order = orderWithRemarks(ORD_1, DELIVER_TOMORROW);
        final Order candidate = orderWithRemarks(ORD_2, "");
        final DuplicateOrderDetectorAdapter adapter = newAdapter();

        final DuplicateOrderCheckResult result = adapter.check(order, List.of(candidate));

        assertThat(result).isEqualTo(DuplicateOrderCheckResult.none());
        verifyNoInteractions(embeddingModel, resilientExecutor);
    }

    @Test
    void shouldFlagTheHighestScoringCandidateWhenAboveThreshold() {

        final Order order = orderWithRemarks(ORD_1, "please deliver tomorrow morning");
        final Order lowSimilarity = orderWithRemarks(ORD_2, UNRELATED_REMARK);
        final Order highSimilarity = orderWithRemarks(ORD_3, "please deliver tomorrow morning, thanks");
        runResilientActionEagerly();
        when(embeddingModel.embed(anyListOfSize(3)))
                .thenReturn(List.of(new float[] { 1, 0 }, new float[] { 0, 1 }, new float[] { 1, 0 }));
        final DuplicateOrderDetectorAdapter adapter = newAdapter();

        final DuplicateOrderCheckResult result = adapter.check(order, List.of(lowSimilarity, highSimilarity));

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getMatchedOrderNumber()).isEqualTo(ORD_3);
        assertThat(result.getSimilarityScore()).isEqualTo(1.0);
    }

    @Test
    void shouldReturnNoneWhenBestSimilarityIsBelowThreshold() {

        final Order order = orderWithRemarks(ORD_1, DELIVER_TOMORROW);
        final Order candidate = orderWithRemarks(ORD_2, UNRELATED_REMARK);
        runResilientActionEagerly();
        when(embeddingModel.embed(anyListOfSize(2))).thenReturn(List.of(new float[] { 1, 0 }, new float[] { 0, 1 }));
        final DuplicateOrderDetectorAdapter adapter = newAdapter();

        final DuplicateOrderCheckResult result = adapter.check(order, List.of(candidate));

        assertThat(result).isEqualTo(DuplicateOrderCheckResult.none());
    }

    @Test
    void shouldTreatZeroVectorAsZeroSimilarityWithoutThrowing() {

        final Order order = orderWithRemarks(ORD_1, DELIVER_TOMORROW);
        final Order candidate = orderWithRemarks(ORD_2, UNRELATED_REMARK);
        runResilientActionEagerly();
        when(embeddingModel.embed(anyListOfSize(2))).thenReturn(List.of(new float[] { 0, 0 }, new float[] { 1, 0 }));
        final DuplicateOrderDetectorAdapter adapter = newAdapter();

        final DuplicateOrderCheckResult result = adapter.check(order, List.of(candidate));

        assertThat(result).isEqualTo(DuplicateOrderCheckResult.none());
    }

    @Test
    void shouldReturnNoneWhenResilienceFails() throws Exception {

        when(resilientExecutor.callResilient(anyString(), any())).thenThrow(new IllegalStateException("circuit open"));
        final Order order = orderWithRemarks(ORD_1, DELIVER_TOMORROW);
        final Order candidate = orderWithRemarks(ORD_2, UNRELATED_REMARK);
        final DuplicateOrderDetectorAdapter adapter = newAdapter();

        final DuplicateOrderCheckResult result = adapter.check(order, List.of(candidate));

        assertThat(result).isEqualTo(DuplicateOrderCheckResult.none());
    }

    private DuplicateOrderDetectorAdapter newAdapter() {

        return new DuplicateOrderDetectorAdapter(embeddingModel, resilientExecutor);
    }

    @SuppressWarnings("unchecked")
    private void runResilientActionEagerly() {

        try {
            when(resilientExecutor.callResilient(anyString(), any())).thenAnswer(invocation -> {
                final Callable<DuplicateOrderCheckResult> action = invocation.getArgument(1);
                return action.call();
            });
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> anyListOfSize(final int size) {

        return org.mockito.ArgumentMatchers.argThat(list -> list != null && list.size() == size);
    }

    private static Order orderWithRemarks(final String orderNumber, final String remarks) {

        final Customer customer = mockCustomer();
        return Order.builder().remarks(remarks).orderNumber(orderNumber).created(new Date()).customer(customer).build();
    }

}
