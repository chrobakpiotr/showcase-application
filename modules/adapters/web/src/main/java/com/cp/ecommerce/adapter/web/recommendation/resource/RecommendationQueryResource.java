package com.cp.ecommerce.adapter.web.recommendation.resource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body naming the customer whose recommendations are asked for; the e-mail travels in the body so that it stays out of
 * URLs, access logs and traces.
 */
public record RecommendationQueryResource(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email) {

    /** Omits the components, which hold personal data, so that framework debug logs of request bodies do not expose them. */
    @Override
    public String toString() {

        return "RecommendationQueryResource[personal data omitted]";
    }
}
