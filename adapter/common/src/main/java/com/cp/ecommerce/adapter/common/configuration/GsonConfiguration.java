package com.cp.ecommerce.adapter.common.configuration;

import com.google.gson.Gson;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a single, shared {@link Gson} bean for all adapters that need JSON (de)serialisation, avoiding repeated
 * instantiation of new {@link Gson} instances across the codebase.
 */
@Configuration
public class GsonConfiguration {

    @Bean
    public Gson gson() {

        return new Gson();
    }

}
