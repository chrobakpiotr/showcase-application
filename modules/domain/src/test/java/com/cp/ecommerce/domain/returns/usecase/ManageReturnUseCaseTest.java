package com.cp.ecommerce.domain.returns.usecase;

import java.math.BigDecimal;
import java.util.List;

import com.cp.ecommerce.adapter.common.exception.ReturnRequestNotApprovableException;
import com.cp.ecommerce.adapter.common.exception.ReturnRequestNotRefundableException;
import com.cp.ecommerce.adapter.common.exception.ReturnRequestNotRejectableException;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.outgoing.FindReturnRequestOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.FindReturnRequestsOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.GenerateReturnNumberOutPort;
import com.cp.ecommerce.domain.returns.port.outgoing.SaveReturnRequestOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private transient SaveReturnRequestOutPort saveReturnRequestOutPort;

    @Mock
    private transient GenerateReturnNumberOutPort generateReturnNumberOutPort;

    @Test
    void shouldCreateRequestedReturnWithGeneratedNumberAndRefundAmount() {

        final ArgumentCaptor<ReturnRequest> captor = ArgumentCaptor.forClass(ReturnRequest.class);
        given(generateReturnNumberOutPort.generate()).willReturn(RETURN_NUMBER);
        given(saveReturnRequestOutPort.save(captor.capture())).willAnswer(invocation -> invocation.getArgument(0));

        final ReturnRequest result = manageReturnUseCase.requestReturn("ORD-1", "SKU-1", 2, "Damaged", new BigDecimal("59.98"));

        assertThat(result.getReturnNumber()).isEqualTo(RETURN_NUMBER);
        assertThat(result.getStatus()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(captor.getValue().getRefundAmount()).isEqualByComparingTo("59.98");
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
    void shouldApproveRequestedReturn() {

        given(findReturnRequestOutPort.find(RETURN_NUMBER)).willReturn(TestDomainObjectFactory.validReturnRequest());
        given(saveReturnRequestOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final ReturnRequest result = manageReturnUseCase.approveReturn(RETURN_NUMBER);

        assertThat(result.getStatus()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(result.getDecidedDate()).isNotNull();
    }

    @Test
    void shouldReturnExistingWhenApprovingAlreadyRefundedReturn() {

        final ReturnRequest refunded = TestDomainObjectFactory.validRefundedReturnRequest();
        given(findReturnRequestOutPort.find(RETURN_NUMBER)).willReturn(refunded);

        assertThat(manageReturnUseCase.approveReturn(RETURN_NUMBER)).isSameAs(refunded);
    }

    @Test
    void shouldRejectRequestedReturn() {

        given(findReturnRequestOutPort.find(RETURN_NUMBER)).willReturn(TestDomainObjectFactory.validReturnRequest());
        given(saveReturnRequestOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final ReturnRequest result = manageReturnUseCase.rejectReturn(RETURN_NUMBER);

        assertThat(result.getStatus()).isEqualTo(ReturnStatus.REJECTED);
    }

    @Test
    void shouldReturnExistingWhenRejectingAlreadyRejectedReturn() {

        final ReturnRequest rejected = TestDomainObjectFactory.validRejectedReturnRequest();
        given(findReturnRequestOutPort.find(RETURN_NUMBER)).willReturn(rejected);

        assertThat(manageReturnUseCase.rejectReturn(RETURN_NUMBER)).isSameAs(rejected);
    }

    @Test
    void shouldNotApproveRejectedReturn() {

        given(findReturnRequestOutPort.find(RETURN_NUMBER)).willReturn(TestDomainObjectFactory.validRejectedReturnRequest());

        assertThatThrownBy(() -> manageReturnUseCase.approveReturn(RETURN_NUMBER))
                .isInstanceOf(ReturnRequestNotApprovableException.class);
    }

    @Test
    void shouldNotRejectApprovedReturn() {

        given(findReturnRequestOutPort.find(RETURN_NUMBER)).willReturn(TestDomainObjectFactory.validApprovedReturnRequest());

        assertThatThrownBy(() -> manageReturnUseCase.rejectReturn(RETURN_NUMBER))
                .isInstanceOf(ReturnRequestNotRejectableException.class);
    }

    @Test
    void shouldMarkApprovedReturnAsRefunded() {

        given(findReturnRequestOutPort.find(RETURN_NUMBER)).willReturn(TestDomainObjectFactory.validApprovedReturnRequest());
        given(saveReturnRequestOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final ReturnRequest result = manageReturnUseCase.markRefunded(RETURN_NUMBER);

        assertThat(result.getStatus()).isEqualTo(ReturnStatus.REFUNDED);
    }

    @Test
    void shouldReturnExistingWhenMarkingAlreadyRefundedReturn() {

        final ReturnRequest refunded = TestDomainObjectFactory.validRefundedReturnRequest();
        given(findReturnRequestOutPort.find(RETURN_NUMBER)).willReturn(refunded);

        assertThat(manageReturnUseCase.markRefunded(RETURN_NUMBER)).isSameAs(refunded);
    }

    @Test
    void shouldNotMarkRequestedReturnAsRefunded() {

        given(findReturnRequestOutPort.find(RETURN_NUMBER)).willReturn(TestDomainObjectFactory.validReturnRequest());

        assertThatThrownBy(() -> manageReturnUseCase.markRefunded(RETURN_NUMBER))
                .isInstanceOf(ReturnRequestNotRefundableException.class);
    }

    @Test
    void shouldReturnNullWhenReturnRequestDoesNotExist() {

        given(findReturnRequestOutPort.find(RETURN_NUMBER)).willReturn(null);

        assertThat(manageReturnUseCase.approveReturn(RETURN_NUMBER)).isNull();
        assertThat(manageReturnUseCase.rejectReturn(RETURN_NUMBER)).isNull();
        assertThat(manageReturnUseCase.markRefunded(RETURN_NUMBER)).isNull();
    }

    @Test
    void shouldDelegateSingleRead() {

        final ReturnRequest returnRequest = TestDomainObjectFactory.validReturnRequest();
        given(findReturnRequestOutPort.find(RETURN_NUMBER)).willReturn(returnRequest);

        assertThat(manageReturnUseCase.getReturn(RETURN_NUMBER)).isSameAs(returnRequest);
        verify(findReturnRequestOutPort).find(RETURN_NUMBER);
    }

}
