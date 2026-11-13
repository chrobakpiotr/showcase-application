package com.cp.ecommerce.adapter.persistence;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;
import com.cp.ecommerce.adapter.persistence.order.dispatch.DispatchRedriveAuditEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.outbox.OrderCancellationRedriveCommandEntity;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntity;
import com.cp.ecommerce.adapter.persistence.review.entity.ReviewEntity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Entity builders hold personal data such as recipients, remarks, reasons and review text, so they must not print it. */
class PersonalDataEntityBuilderToStringTest {

    private static final String SECRET = "jan.kowalski@example.com +48 600 700 800";

    static Stream<Class<?>> personalDataEntities() {

        return Stream.of(
                NotificationEntity.class,
                OrderEntity.class,
                ReturnRequestEntity.class,
                ReviewEntity.class,
                DispatchRedriveAuditEntity.class,
                OrderCancellationRedriveCommandEntity.class);
    }

    @ParameterizedTest
    @MethodSource("personalDataEntities")
    void shouldOmitFieldsFromBuilderToString(final Class<?> type) throws ReflectiveOperationException {

        final Object builder = type.getMethod("builder").invoke(null);
        for (final Method setter : builder.getClass().getMethods()) {
            if (setter.getParameterCount() == 1 && setter.getParameterTypes()[0] == String.class) {
                setter.invoke(builder, SECRET);
            }
        }

        assertThat(builder.toString()).isEqualTo(type.getSimpleName() + "Builder[personal data omitted]");
    }
}
