package com.cp.ecommerce.adapter.web;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.stream.Stream;

import com.cp.ecommerce.adapter.web.assistant.resource.SupportAnswerResource;
import com.cp.ecommerce.adapter.web.assistant.resource.SupportQuestionResource;
import com.cp.ecommerce.adapter.web.notifications.resource.NotificationResource;
import com.cp.ecommerce.adapter.web.notifications.resource.RecipientSearchResource;
import com.cp.ecommerce.adapter.web.order.resource.AnalyticsAnswerResource;
import com.cp.ecommerce.adapter.web.order.resource.AnalyticsQuestionResource;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderDetailsResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.adapter.web.recommendation.resource.RecommendationQueryResource;
import com.cp.ecommerce.adapter.web.returns.resource.RequestReturnResource;
import com.cp.ecommerce.adapter.web.returns.resource.ReturnRequestResource;
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
                NotificationResource.class,
                RequestReturnResource.class,
                ReturnRequestResource.class,
                SupportAnswerResource.class,
                AnalyticsAnswerResource.class,
                RecommendationQueryResource.class,
                RecipientSearchResource.class);
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

    static Stream<Class<? extends Record>> personalDataResourcesWithBuilders() {

        return personalDataResources().filter(PersonalDataResourceToStringTest::hasBuilder);
    }

    @ParameterizedTest
    @MethodSource("personalDataResourcesWithBuilders")
    void shouldOmitComponentsFromBuilderToString(final Class<? extends Record> type) throws ReflectiveOperationException {

        final Object builder = type.getMethod("builder").invoke(null);
        for (final Method setter : builder.getClass().getMethods()) {
            if (setter.getParameterCount() == 1 && setter.getParameterTypes()[0] == String.class) {
                setter.invoke(builder, SECRET);
            }
        }

        assertThat(builder.toString()).isEqualTo(type.getSimpleName() + "Builder[personal data omitted]");
    }

    private static boolean hasBuilder(final Class<?> type) {

        return Arrays.stream(type.getMethods()).anyMatch(method -> "builder".equals(method.getName()));
    }

    private static Object sample(final Class<?> parameterType) {

        if (parameterType == String.class) {
            return SECRET;
        }
        if (parameterType == boolean.class) {
            return false;
        }
        return parameterType == int.class ? 0 : null;
    }
}
