package com.cp.ecommerce.domain.order.usecase;

import java.util.List;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PageQuery;
import com.cp.ecommerce.domain.order.PagedResult;
import com.cp.ecommerce.domain.order.port.outgoing.FindOrdersOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Tests for {@link ListOrdersUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ListOrdersUseCaseTest {

    @Mock
    private transient FindOrdersOutPort findOrdersOutPort;

    @InjectMocks
    private transient ListOrdersUseCase listOrdersUseCase;

    @Test
    void shouldDelegateToOutgoingPort() {

        final PageQuery pageQuery = new PageQuery(0, 20);
        final PagedResult<Order> expected = new PagedResult<>(List.of(TestDomainObjectFactory.validOrder()), 0, 20, 1, 1);
        given(findOrdersOutPort.findAll(pageQuery)).willReturn(expected);

        final PagedResult<Order> result = listOrdersUseCase.listOrders(pageQuery);

        assertThat(result).isSameAs(expected);
    }

}
