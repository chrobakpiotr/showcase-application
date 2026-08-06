package com.cp.ecommerce.adapter.camel.order;

import com.cp.ecommerce.adapter.camel.configuration.CamelProperties;
import com.cp.ecommerce.domain.order.Order;
import com.google.gson.Gson;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Camel route that fans a placed-order notification out to fulfillment channels using enterprise integration patterns:
 * <ul>
 * <li><b>Wire Tap</b> - every order is copied, fire-and-forget, to an audit trail file without affecting the main flow.</li>
 * <li><b>Content-Based Router</b> - orders shipping to the home market ({@link CamelProperties#getDomesticCountryCode()}) are
 * routed to the domestic fulfillment channel, all others to the international one.</li>
 * </ul>
 * Each channel is modeled here as a local JSON file drop (see {@link CamelProperties#getNotificationDirectory()}), which keeps
 * the showcase runnable with zero external infrastructure; in a real deployment the {@code file:} endpoints below would be
 * swapped for e.g. {@code sjms:}, {@code http:} or {@code ftp:} endpoints without touching the routing logic itself.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "service.camel.enabled", havingValue = "true")
public class OrderNotificationRoutes extends RouteBuilder {

    /** Entry point of the route: send an {@link Order} here to have it audited and routed. */
    public static final String ORDER_NOTIFICATION_ENDPOINT = "direct:orderNotification";

    static final String AUDIT_ENDPOINT = "direct:orderNotificationAudit";

    static final String DOMESTIC_ENDPOINT = "direct:domesticOrderFulfillment";

    static final String INTERNATIONAL_ENDPOINT = "direct:internationalOrderFulfillment";

    private static final String ORDER_NUMBER_HEADER = "orderNumber";

    private final CamelProperties camelProperties;

    private final Gson gson;

    @Override
    public void configure() {

        from(ORDER_NOTIFICATION_ENDPOINT).routeId("orderNotificationRoute")
                .wireTap(AUDIT_ENDPOINT)
                .choice()
                .when(this::isDomesticOrder)
                .to(DOMESTIC_ENDPOINT)
                .otherwise()
                .to(INTERNATIONAL_ENDPOINT)
                .end();

        from(AUDIT_ENDPOINT).routeId("orderNotificationAuditRoute").process(this::marshalToJson).to(fileEndpoint("audit"));

        from(DOMESTIC_ENDPOINT).routeId("domesticOrderFulfillmentRoute")
                .process(this::marshalToJson)
                .to(fileEndpoint("domestic"));

        from(INTERNATIONAL_ENDPOINT).routeId("internationalOrderFulfillmentRoute")
                .process(this::marshalToJson)
                .to(fileEndpoint("international"));
    }

    private boolean isDomesticOrder(final Exchange exchange) {

        final Order order = exchange.getIn().getBody(Order.class);
        final String countryCode = order.getCustomer().getAddress().getCountryCode();
        return camelProperties.getDomesticCountryCode().equalsIgnoreCase(countryCode);
    }

    private void marshalToJson(final Exchange exchange) {

        final Order order = exchange.getIn().getBody(Order.class);
        exchange.getIn().setHeader(ORDER_NUMBER_HEADER, order.getOrderNumber());
        exchange.getIn().setBody(gson.toJson(order));
    }

    private String fileEndpoint(final String channel) {

        return "file:" + camelProperties.getNotificationDirectory() + "/" + channel + "?fileName=${header."
                + ORDER_NUMBER_HEADER + "}.json";
    }

}
