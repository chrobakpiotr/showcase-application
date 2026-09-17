package com.cp.ecommerce.adapter.persistence.order.outbox.metrics;

import java.util.Map;

import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for {@link MicrometerRemarksClassificationSummaryAdapter}. Uses a real {@link SimpleMeterRegistry} rather than a
 * mock: the adapter's whole job is Micrometer's own tag-based counter lookup semantics (a category counter that was never
 * incremented simply does not exist yet), which a mock would not exercise faithfully.
 */
class MicrometerRemarksClassificationSummaryAdapterTest {

    private transient SimpleMeterRegistry meterRegistry;

    private transient MicrometerRemarksClassificationSummaryAdapter adapter;

    @BeforeEach
    void setUp() {

        meterRegistry = new SimpleMeterRegistry();
        adapter = new MicrometerRemarksClassificationSummaryAdapter(meterRegistry);
    }

    @Test
    void shouldReturnZeroForEveryCategoryWhenNoneWereEverClassified() {

        final RemarksClassificationSummary summary = adapter.getSummary();

        assertThat(summary.countsByCategory()).containsOnlyKeys(RemarksTriageCategory.values());
        assertThat(summary.countsByCategory().values()).allMatch(count -> count == 0L);
    }

    @Test
    void shouldReflectRecordedClassificationCountsPerCategory() {

        incrementRemarksClassification(RemarksTriageCategory.STANDARD, 10);
        incrementRemarksClassification(RemarksTriageCategory.URGENT, 2);
        incrementRemarksClassification(RemarksTriageCategory.SUSPICIOUS, 1);

        final RemarksClassificationSummary summary = adapter.getSummary();

        assertThat(summary.countsByCategory()).isEqualTo(
                Map.of(
                        RemarksTriageCategory.STANDARD,
                        10L,
                        RemarksTriageCategory.URGENT,
                        2L,
                        RemarksTriageCategory.COMPLAINT,
                        0L,
                        RemarksTriageCategory.SUSPICIOUS,
                        1L));
    }

    private void incrementRemarksClassification(final RemarksTriageCategory category, final int times) {

        final Counter counter = Counter.builder(SagaMetrics.REMARKS_CLASSIFICATION_METRIC_NAME)
                .tag("category", category.name())
                .register(meterRegistry);
        for (int i = 0; i < times; i++) {
            counter.increment();
        }
    }

}
