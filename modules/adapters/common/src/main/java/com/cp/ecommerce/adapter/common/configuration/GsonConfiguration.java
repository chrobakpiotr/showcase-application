package com.cp.ecommerce.adapter.common.configuration;

import java.io.IOException;
import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

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

        final TypeAdapter<Instant> instantAdapter = new TypeAdapter<>() {

            @Override
            public void write(final JsonWriter out, final Instant value) throws IOException {

                out.value(value.toString());
            }

            @Override
            public Instant read(final JsonReader in) throws IOException {

                return Instant.parse(in.nextString());
            }
        };
        return new GsonBuilder().registerTypeAdapter(Instant.class, instantAdapter.nullSafe()).create();
    }

}
