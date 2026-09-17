package com.cp.ecommerce.adapter.amqp.order.mapper;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

import static com.cp.ecommerce.domain.order.OrderMessage.SCHEMA_VERSION;

/**
 * {@link OrderMessage} mapper tests.
 */
@ExtendWith(MockitoExtension.class)
class OrderMessageMapperTest {

    @InjectMocks
    transient OrderMessageMapper mapper;

    @Test
    void shouldSetDefaultValues() {

        final OrderMessage message = mapper.mapToMessage(OrderBuilder.mockOrder()).orElse(null);
        assertThat(message.schemaVersion()).isEqualTo(SCHEMA_VERSION);
    }

    @Test
    void shouldReturnEmptyDomainObject() {

        final Order order = mapper.mapToDomainObject(OrderMessage.builder().build()).orElse(null);
        assertThat(order).isNull();
    }

}
