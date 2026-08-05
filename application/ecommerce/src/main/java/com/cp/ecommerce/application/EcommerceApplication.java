package com.cp.ecommerce.application;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import lombok.extern.slf4j.Slf4j;

/**
 * Starting point of the E-commerce application.
 */
@Slf4j
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
@ComponentScan(
        basePackages = {
                "com.cp.ecommerce.adapter.web",
                "com.cp.ecommerce.adapter.mail",
                "com.cp.ecommerce.adapter.persistence",
                "com.cp.ecommerce.domain",
                "com.cp.ecommerce.adapter.amqp",
                "com.cp.ecommerce.adapter.aws",
                "com.cp.ecommerce.adapter.kafka",
                "com.cp.ecommerce.adapter.common", })
public class EcommerceApplication {

    public static void main(final String... args) {

        setDefaults();
        new SpringApplication(EcommerceApplication.class).run(args);
        log.info("E-commerce app started...");
    }

    private static void setDefaults() {

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

}
