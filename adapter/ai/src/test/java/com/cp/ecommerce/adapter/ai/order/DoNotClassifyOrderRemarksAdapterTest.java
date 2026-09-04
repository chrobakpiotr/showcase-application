package com.cp.ecommerce.adapter.ai.order;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.RemarksTriageResult;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests for {@link DoNotClassifyOrderRemarksAdapter}.
 */
class DoNotClassifyOrderRemarksAdapterTest {

    @Test
    void shouldReturnStandardClassificationWithoutCallingAnyModel() {

        final DoNotClassifyOrderRemarksAdapter adapter = new DoNotClassifyOrderRemarksAdapter();
        final Order order = mockOrder();

        final RemarksTriageResult result = adapter.classify(order);

        assertThat(result.getCategory()).isEqualTo(RemarksTriageCategory.STANDARD);
    }

}
