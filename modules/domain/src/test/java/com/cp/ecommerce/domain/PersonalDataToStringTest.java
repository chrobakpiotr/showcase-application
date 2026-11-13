package com.cp.ecommerce.domain;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.cp.ecommerce.domain.assistant.SupportAnswer;
import com.cp.ecommerce.domain.assistant.SupportQuestion;
import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.order.AnalyticsAnswer;
import com.cp.ecommerce.domain.order.AnalyticsQuestion;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.recommendation.CustomerReviewProfile;
import com.cp.ecommerce.domain.recommendation.RecommendationRequest;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnRequestCommand;
import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Domain objects can reach logs through string concatenation, so their toString must leave personal data out. */
class PersonalDataToStringTest {

    static Stream<Arguments> personalFields() {

        return Stream.of(
                Arguments.of(Contact.class, List.of("fullName", "email", "phone")),
                Arguments.of(Address.class, List.of("street", "postalCode", "city")),
                Arguments.of(Order.class, List.of("remarks")),
                Arguments.of(SupportQuestion.class, List.of("question")),
                Arguments.of(SupportAnswer.class, List.of("answer")),
                Arguments.of(AnalyticsQuestion.class, List.of("question")),
                Arguments.of(AnalyticsAnswer.class, List.of("answer")),
                Arguments.of(CustomerReviewProfile.class, List.of("comment")),
                Arguments.of(RecommendationRequest.class, List.of("customerEmail")),
                Arguments.of(Notification.class, List.of("recipientEmail")),
                Arguments.of(Review.class, List.of("authorName", "comment")),
                Arguments.of(ReturnRequest.class, List.of("reason")),
                Arguments.of(ReturnRequestCommand.class, List.of("reason")));
    }

    @ParameterizedTest
    @MethodSource("personalFields")
    void shouldLeavePersonalFieldsOutOfToString(final Class<?> type, final List<String> fields) throws Exception {

        final Constructor<?> constructor = Arrays.stream(type.getDeclaredConstructors())
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElseThrow();
        constructor.setAccessible(true);
        final Class<?>[] parameterTypes = constructor.getParameterTypes();
        final Object[] arguments = Arrays.stream(parameterTypes).map(PersonalDataToStringTest::sample).toArray();

        final String text = constructor.newInstance(arguments).toString();

        assertThat(fields).allSatisfy(field -> assertThat(text).doesNotContain(field + "="));
    }

    @ParameterizedTest
    @MethodSource("personalFields")
    void shouldLeavePersonalDataOutOfBuilderToString(final Class<?> type, final List<String> fields) throws Exception {

        if (type.isRecord()) {
            return;
        }
        final Object builder = type.getMethod("builder").invoke(null);

        assertThat(builder).hasToString(type.getSimpleName() + "Builder[personal data omitted]");
    }

    @Test
    void shouldLeaveAFilledContactBuilderWithoutItsData() {

        final Contact.ContactBuilder builder = Contact.builder().fullName("Jan Kowalski").email("jan@example.com");

        assertThat(builder.toString()).doesNotContain("Jan Kowalski", "jan@example.com");
    }

    @Test
    void shouldLeaveContactAndAddressDataOutOfAnOrder() {

        final Order order = TestDomainObjectFactory.validOrder();

        assertThat(order.toString()).contains(TestDomainObjectFactory.TEST_ORDER_NUMBER)
                .doesNotContain("John Doe", "john.doe@test.com", "+48 123 456 789", "Main Street 1", "12-345", "Warsaw")
                .doesNotContain("remark");
    }

    @Test
    void shouldDescribeAReturnCommandByItsOrderLine() {

        final ReturnRequestCommand command = new ReturnRequestCommand("ORD-1", "SKU-1", 1, 2, "call me", BigDecimal.ONE);

        assertThat(command).hasToString("ReturnRequestCommand[orderNumber=ORD-1, sku=SKU-1, quantity=1]");
    }

    private static Object sample(final Class<?> parameterType) {

        if (parameterType == String.class) {
            return "personal";
        }
        return parameterType.isPrimitive() ? Array.get(Array.newInstance(parameterType, 1), 0) : null;
    }
}
