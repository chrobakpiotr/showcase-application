package com.cp.ecommerce.adapter.persistence.configuration;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.incrementer.H2SequenceMaxValueIncrementer;
import org.springframework.jdbc.support.incrementer.PostgresSequenceMaxValueIncrementer;

import lombok.RequiredArgsConstructor;

/**
 * Sequence number configuration class
 */
@Configuration
@RequiredArgsConstructor
public class SequenceNumberConfiguration {

    private static final String SEQ_ORDER_NUMBER = "TEST_DB.SEQ_ORDER_NUMBER";

    private final DataSource dataSource;

    @Bean
    PostgresSequenceMaxValueIncrementer postgresSequenceMaxValueIncrementer() {
        return new PostgresSequenceMaxValueIncrementer(dataSource, SEQ_ORDER_NUMBER);
    }

    @Bean
    H2SequenceMaxValueIncrementer h2SequenceMaxValueIncrementer() {
        return new H2SequenceMaxValueIncrementer(dataSource, SEQ_ORDER_NUMBER);
    }
}
