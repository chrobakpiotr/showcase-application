package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.RemarksTriageResult;
import com.cp.ecommerce.domain.order.port.outgoing.ClassifyOrderRemarksOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ClassifyOrderRemarksUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ClassifyOrderRemarksUseCaseTest {

    @Mock
    private transient ClassifyOrderRemarksOutPort classifyOrderRemarksOutPort;

    @InjectMocks
    private transient ClassifyOrderRemarksUseCase classifyOrderRemarksUseCase;

    @Test
    void shouldDelegateClassificationAndReturnItsResult() {

        final Order order = TestDomainObjectFactory.validOrder();
        final RemarksTriageResult expected = RemarksTriageResult.builder()
                .category(RemarksTriageCategory.URGENT)
                .rationale("needed by tomorrow")
                .build();
        when(classifyOrderRemarksOutPort.classify(order)).thenReturn(expected);

        final RemarksTriageResult actual = classifyOrderRemarksUseCase.classifyRemarks(order);

        assertThat(actual).isEqualTo(expected);
        verify(classifyOrderRemarksOutPort).classify(order);
    }

}
