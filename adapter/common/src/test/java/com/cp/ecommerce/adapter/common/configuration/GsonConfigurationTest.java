package com.cp.ecommerce.adapter.common.configuration;

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

}
