package com.cp.ecommerce.adapter.common.configuration;

import java.time.Instant;

import com.google.gson.Gson;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GsonConfiguration}.
 */
class GsonConfigurationTest {

    private final transient GsonConfiguration configuration = new GsonConfiguration();

    @Test
    void shouldCreateWorkingGsonInstance() {

        final Gson gson = configuration.gson();

        assertThat(gson).isNotNull();
        assertThat(gson.toJson("sample")).isEqualTo("\"sample\"");
    }

    @Test
    void shouldRoundTripInstantAsIso8601() {

        final Gson gson = configuration.gson();
        final Instant instant = Instant.parse("2026-09-19T12:00:00Z");

        final String json = gson.toJson(instant);

        assertThat(json).isEqualTo("\"2026-09-19T12:00:00Z\"");
        assertThat(gson.fromJson(json, Instant.class)).isEqualTo(instant);
    }

}
