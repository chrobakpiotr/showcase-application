package com.cp.ecommerce.application.returns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundReturnContinuationCompletionServiceTest {

    private static final String RETURN_NUMBER = "RET-1";

    @Mock
    private ReturnStateNotificationTransaction returnStateNotificationTransaction;

    @Test
    void shouldDelegateContinuationCompletionToAtomicReturnTransaction() {

        final var service = new RefundReturnContinuationCompletionService(returnStateNotificationTransaction);

        service.complete(RETURN_NUMBER);

        verify(returnStateNotificationTransaction).markRefundedAndNotify(RETURN_NUMBER);
    }
}
