package com.cp.ecommerce.adapter.web.order;

import java.util.List;
import java.util.stream.Collectors;

import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_CITY;
import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_COUNTRY_CODE;
import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_EMAIL;
import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_FULL_NAME;
import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_PHONE_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_POSTAL_CODE;
import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_STREET_ADDRESS;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_LINE_ITEM_PRODUCT_NAME;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_LINE_ITEM_SKU;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_REMARKS;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

class LogOrderAdapterTest {

    private final LogOrderAdapter logOrderAdapter = new LogOrderAdapter();

    @Test
    void shouldLogOnlyBoundedOrderSummary() {
        final List<ILoggingEvent> events = captureEvents(() -> logOrderAdapter.log(mockOrder()));
        final String output = events.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));

        assertThat(output).contains("Order placed: status=CONFIRMED, itemCount=1");
        assertThat(output).doesNotContain(
                TEST_EMAIL,
                TEST_STREET_ADDRESS,
                TEST_ORDER_NUMBER,
                TEST_REMARKS,
                TEST_ORDER_LINE_ITEM_SKU,
                TEST_ORDER_LINE_ITEM_PRODUCT_NAME,
                TEST_FULL_NAME,
                TEST_PHONE_NUMBER,
                TEST_CITY,
                TEST_POSTAL_CODE,
                TEST_COUNTRY_CODE);
        assertThat(events).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    }

    @Test
    void shouldNotLogRawExceptionDetails() {
        final Order order = mock(Order.class);
        given(order.getStatus()).willThrow(new IllegalStateException("RAW_EXCEPTION_MARKER"));

        final List<ILoggingEvent> events = captureEvents(() -> logOrderAdapter.log(order));
        final String output = events.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));

        assertThat(output).contains("Unable to create bounded order summary");
        assertThat(output).doesNotContain("RAW_EXCEPTION_MARKER");
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getLevel()).isEqualTo(Level.WARN);
        assertThat(events.getFirst().getThrowableProxy()).isNull();
    }

    private List<ILoggingEvent> captureEvents(final Runnable operation) {
        final Logger logger = (Logger) LoggerFactory.getLogger(LogOrderAdapter.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            operation.run();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        return List.copyOf(appender.list);
    }
}
