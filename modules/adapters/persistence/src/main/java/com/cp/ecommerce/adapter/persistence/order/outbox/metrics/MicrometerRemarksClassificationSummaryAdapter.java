package com.cp.ecommerce.adapter.persistence.order.outbox.metrics;

import java.util.EnumMap;
import java.util.Map;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Implementation of {@link GetRemarksClassificationSummaryOutPort} that reads back the same in-process
 * {@code saga.order-placement.remarks-classifications} Micrometer counter {@link SagaMetrics#recordRemarksClassification}
 * already writes to, one lookup per {@link RemarksTriageCategory} tag value - see ADR 0021.
 *
 * <p>
 * Deliberately not a separate persisted aggregate: {@code SagaMetrics} already turns every classification into a
 * Grafana/Prometheus-visible counter, so re-deriving a summary from a second, independently-persisted table would be pure
 * duplication for a value that is only ever read back as an approximate, single-process snapshot to answer an ops question -
 * exactly the read-model-reuse reasoning ADR 0014 already applied to the order-analytics projection itself.
 * </p>
 *
 * <p>
 * Gated on the same {@code outbox.publisher.enabled} property as {@link SagaMetrics} (rather than being unconditional): this
 * adapter only makes sense when the counter it reads is actually being written to, and sharing the exact same condition keeps
 * both always either present or absent together instead of one silently reading a counter the other was never asked to write.
 * </p>
 */
@PersistenceAdapter
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
class MicrometerRemarksClassificationSummaryAdapter implements GetRemarksClassificationSummaryOutPort {

    private final transient MeterRegistry meterRegistry;

    MicrometerRemarksClassificationSummaryAdapter(final MeterRegistry meterRegistry) {

        this.meterRegistry = meterRegistry;
    }

    @Override
    public RemarksClassificationSummary getSummary() {

        final Map<RemarksTriageCategory, Long> countsByCategory = new EnumMap<>(RemarksTriageCategory.class);
        for (final RemarksTriageCategory category : RemarksTriageCategory.values()) {
            countsByCategory.put(category, countFor(category));
        }
        return new RemarksClassificationSummary(countsByCategory);
    }

    /**
     * A category whose classification has never occurred yet has no registered counter at all - {@code
     * MeterRegistry.find(...).counter()} then returns {@code null} rather than a zero-valued counter, which is handled here as
     * a plain 0 rather than propagated as a missing value the caller would otherwise need to null-check.
     */
    private long countFor(final RemarksTriageCategory category) {

        final Counter counter = meterRegistry.find(SagaMetrics.REMARKS_CLASSIFICATION_METRIC_NAME)
                .tag("category", category.name())
                .counter();
        return counter == null ? 0L : (long) counter.count();
    }

}
