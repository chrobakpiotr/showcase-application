package com.cp.ecommerce.domain.returns.usecase;

import java.math.BigDecimal;
import java.util.List;

import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.outgoing.FindReturnRequestOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.FindReturnRequestsOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.GenerateReturnNumberOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.ManageReturnRequestStateOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ManageReturnUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ManageReturnUseCaseTest {

    private static final String RETURN_NUMBER = "RETURN-1";

    @InjectMocks
    private transient ManageReturnUseCase manageReturnUseCase;

    @Mock
    private transient FindReturnRequestOutPort findReturnRequestOutPort;

    @Mock
    private transient FindReturnRequestsOutPort findReturnRequestsOutPort;

    @Mock
    private transient ManageReturnRequestStateOutPort manageReturnRequestStateOutPort;

    @Mock
    private transient GenerateReturnNumberOutPort generateReturnNumberOutPort;

    @Test
    void shouldCreateRequestedReturnWithGeneratedNumberAndRefundAmount() {

        final ArgumentCaptor<ReturnRequest> captor = ArgumentCaptor.forClass(ReturnRequest.class);
        given(generateReturnNumberOutPort.generate()).willReturn(RETURN_NUMBER);
        given(manageReturnRequestStateOutPort.create(captor.capture(), eq(3)))
                .willAnswer(invocation -> invocation.getArgument(0));

        final ReturnRequest result = manageReturnUseCase
                .requestReturn("ORD-1", "SKU-1", 2, 3, "Damaged", new BigDecimal("59.98"));

        assertThat(result.getReturnNumber()).isEqualTo(RETURN_NUMBER);
        assertThat(result.getStatus()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("59.98");
        verify(manageReturnRequestStateOutPort).create(captor.getValue(), 3);
    }

    @Test
    void shouldCreateRequestedReturnFromFullLineEntitlement() {

        final ArgumentCaptor<ReturnRequest> captor = ArgumentCaptor.forClass(ReturnRequest.class);
        given(generateReturnNumberOutPort.generate()).willReturn(RETURN_NUMBER);
        given(manageReturnRequestStateOutPort.createFromLineEntitlement(captor.capture(), eq(3)))
                .willAnswer(invocation -> invocation.getArgument(0));

        final ReturnRequest result = manageReturnUseCase
                .requestReturnFromLineEntitlement("ORD-1", "SKU-1", 2, 3, "Damaged", new BigDecimal("89.97"));

        assertThat(result.getReturnNumber()).isEqualTo(RETURN_NUMBER);
        assertThat(result.getStatus()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("89.97");
        verify(manageReturnRequestStateOutPort).createFromLineEntitlement(captor.getValue(), 3);
    }

    @Test
    void shouldListReturns() {

        given(findReturnRequestsOutPort.findAll()).willReturn(List.of(TestDomainObjectFactory.validReturnRequest()));

        assertThat(manageReturnUseCase.listReturns()).hasSize(1);
    }

    @Test
    void shouldListPendingReturns() {

        given(findReturnRequestsOutPort.findPending()).willReturn(List.of(TestDomainObjectFactory.validReturnRequest()));

        assertThat(manageReturnUseCase.listPendingReturns()).hasSize(1);
    }

    @Test
    void shouldListReturnsForOrder() {

        given(findReturnRequestsOutPort.findByOrderNumber(TestDomainObjectFactory.TEST_ORDER_NUMBER))
                .willReturn(List.of(TestDomainObjectFactory.validReturnRequest()));

        assertThat(manageReturnUseCase.listReturnsForOrder(TestDomainObjectFactory.TEST_ORDER_NUMBER)).hasSize(1);
    }

    @Test
    void shouldDelegateApprove() {

        final ReturnRequest approved = TestDomainObjectFactory.validApprovedReturnRequest();
        given(manageReturnRequestStateOutPort.approve(RETURN_NUMBER)).willReturn(approved);

        assertThat(manageReturnUseCase.approveReturn(RETURN_NUMBER)).isSameAs(approved);
    }

    @Test
    void shouldDelegateReject() {

        final ReturnRequest rejected = TestDomainObjectFactory.validRejectedReturnRequest();
        given(manageReturnRequestStateOutPort.reject(RETURN_NUMBER)).willReturn(rejected);

        assertThat(manageReturnUseCase.rejectReturn(RETURN_NUMBER)).isSameAs(rejected);
    }

    @Test
    void shouldDelegateMarkRefunded() {

        final ReturnRequest refunded = TestDomainObjectFactory.validRefundedReturnRequest();
        given(manageReturnRequestStateOutPort.markRefunded(RETURN_NUMBER)).willReturn(refunded);

        assertThat(manageReturnUseCase.markRefunded(RETURN_NUMBER)).isSameAs(refunded);
    }

    @Test
    void shouldDelegateSingleRead() {

        final ReturnRequest returnRequest = TestDomainObjectFactory.validReturnRequest();
        given(findReturnRequestOutPort.find(RETURN_NUMBER)).willReturn(returnRequest);

        assertThat(manageReturnUseCase.getReturn(RETURN_NUMBER)).isSameAs(returnRequest);
        verify(findReturnRequestOutPort).find(RETURN_NUMBER);
    }

}
