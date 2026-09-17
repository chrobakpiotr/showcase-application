package com.cp.ecommerce.adapter.mail.freemarker;

import java.util.Collections;
import java.util.Optional;

import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cases for {@link OrderCustomObjectWrapperFactory} class.
 */
@ExtendWith(MockitoExtension.class)
class OrderCustomObjectWrapperFactoryTest {

    @Mock
    private transient FreeMarkerMessageSource messageSource;

    @Mock
    private transient FreeMarkerCustomObjectWrapper objectWrapper;

    @Test
    public void shouldReturnNullInstanceIfNullAsObject() {

        assertThat(createOrderCustomObjectWrapper(null)).isNull();
    }

    @Test
    public void shouldReturnNullInstanceIfEmptyOptionalAsObject() {

        assertThat(createOrderCustomObjectWrapper(Optional.empty())).isNull();
    }

    @Test
    public void shouldReturnNotNullInstanceIfOrderAsObject() {

        assertThat(createOrderCustomObjectWrapper(Order.builder().build())).isNotNull();
    }

    @Test
    public void shouldReturnNotNullInstanceIfOrderOptionalAsObject() {

        assertThat(createOrderCustomObjectWrapper(Optional.of(Order.builder().build()))).isNotNull();
    }

    @Test
    public void shouldReturnNullInstanceIfOrderIsOfMapType() {

        assertThat(createOrderCustomObjectWrapper(Collections.<String, String> emptyMap())).isNotNull();
    }

    @Test
    public void shouldReturnNullInstanceIfOrderIsOfOtherType() {

        assertThat(createOrderCustomObjectWrapper(5L)).isNull();
    }

    private OrderCustomObjectWrapperFactory.OrderCustomObjectWrapper createOrderCustomObjectWrapper(final Object model) {

        return new OrderCustomObjectWrapperFactory(messageSource).createWrapperBasedOnModelType(model, objectWrapper);
    }

}
