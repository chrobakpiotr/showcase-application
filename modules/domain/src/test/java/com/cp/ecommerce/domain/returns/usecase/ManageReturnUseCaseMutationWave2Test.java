package com.cp.ecommerce.domain.returns.usecase;

import java.math.BigDecimal;
import java.util.List;

import com.cp.ecommerce.domain.returns.PageQuery;
import com.cp.ecommerce.domain.returns.PagedResult;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.port.outgoing.FindReturnRequestOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.FindReturnRequestsOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.GenerateReturnNumberOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.ManageReturnRequestStateOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageReturnUseCaseMutationWave2Test {

    @Mock
    private FindReturnRequestOutPort findReturnRequestOutPort;
    @Mock
    private FindReturnRequestsOutPort findReturnRequestsOutPort;
    @Mock
    private ManageReturnRequestStateOutPort manageReturnRequestStateOutPort;
    @Mock
    private GenerateReturnNumberOutPort generateReturnNumberOutPort;

    private ManageReturnUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManageReturnUseCase(
                findReturnRequestOutPort,
                findReturnRequestsOutPort,
                manageReturnRequestStateOutPort,
                generateReturnNumberOutPort);
    }

    @Test
    void shouldDelegateAllPagedReadsExactly() {
        final PageQuery query = new PageQuery(0, 20);
        final ReturnRequest request = TestDomainObjectFactory.validReturnRequest();
        final PagedResult<ReturnRequest> page = new PagedResult<>(List.of(request), 0, 20, 1, 1);
        given(findReturnRequestsOutPort.findAll(query)).willReturn(page);
        given(findReturnRequestsOutPort.findPending(query)).willReturn(page);
        given(findReturnRequestsOutPort.findByOrderNumber(TestDomainObjectFactory.TEST_ORDER_NUMBER, query)).willReturn(page);

        assertThat(useCase.listReturns(query)).isSameAs(page);
        assertThat(useCase.listPendingReturns(query)).isSameAs(page);
        assertThat(useCase.listReturnsForOrder(TestDomainObjectFactory.TEST_ORDER_NUMBER, query)).isSameAs(page);
    }

    @Test
    void shouldValidateRequestedReturnBeforeStateMutation() {
        given(generateReturnNumberOutPort.generate()).willReturn("RETURN-X");

        assertThatThrownBy(() -> useCase.requestReturn("ORDER-1", "SKU-1", 1, 1, " ", new BigDecimal("10.00")))
                .isInstanceOf(DomainObjectValidationException.class);

        verify(manageReturnRequestStateOutPort, never()).create(any(), any(Integer.class));
    }
}
