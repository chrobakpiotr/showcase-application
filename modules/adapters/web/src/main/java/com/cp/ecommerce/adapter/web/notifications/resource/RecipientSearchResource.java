package com.cp.ecommerce.adapter.web.notifications.resource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body naming the notification recipient to search for; the e-mail travels in the body so that it stays out of URLs,
 * access logs and traces.
 */
public record RecipientSearchResource(@Schema(example = "jane.doe@example.com") String recipientEmail) {

    /** Omits the components, which hold personal data, so that framework debug logs of request bodies do not expose them. */
    @Override
    public String toString() {

        return "RecipientSearchResource[personal data omitted]";
    }
}
