package com.cp.ecommerce.adapter.web;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.stream.Stream;

import com.cp.ecommerce.adapter.web.assistant.resource.SupportQuestionResource;
import com.cp.ecommerce.adapter.web.notifications.resource.NotificationResource;
import com.cp.ecommerce.adapter.web.order.resource.AnalyticsQuestionResource;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderDetailsResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.adapter.web.review.resource.ReviewResource;
import com.cp.ecommerce.adapter.web.review.resource.SubmitReviewResource;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Request and response records with personal data must not print it, because Spring logs bodies at debug level. */
class PersonalDataResourceToStringTest {

    private static final String SECRET = "jan.kowalski@example.com +48 600 700 800";

    static Stream<Class<? extends Record>> personalDataResources() {

        return Stream.of(
                CustomerResource.class,
                OrderResource.class,
                OrderDetailsResource.class,
                ReviewResource.class,
                SubmitReviewResource.class,
                SupportQuestionResource.class,
                AnalyticsQuestionResource.class,
                NotificationResource.class);
    }

    @ParameterizedTest
    @MethodSource("personalDataResources")
    void shouldOmitComponentsFromToString(final Class<? extends Record> type) throws ReflectiveOperationException {

        final RecordComponent[] components = type.getRecordComponents();
        final Class<?>[] parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        final Constructor<? extends Record> constructor = type.getDeclaredConstructor(parameterTypes);
        final Object[] arguments = Arrays.stream(parameterTypes).map(PersonalDataResourceToStringTest::sample).toArray();

        final Record resource = constructor.newInstance(arguments);

        assertThat(resource.toString()).isEqualTo(type.getSimpleName() + "[personal data omitted]").doesNotContain(SECRET);
    }

    private static Object sample(final Class<?> parameterType) {

        if (parameterType == String.class) {
            return SECRET;
        }
        return parameterType == int.class ? 0 : null;
    }
}
