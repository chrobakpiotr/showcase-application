package com.cp.ecommerce.adapter.persistence.configuration;

import java.time.Clock;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.task.SimpleAsyncTaskSchedulerCustomizer;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Persistence configuration class.
 */
@Configuration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "com.cp.ecommerce.adapter.persistence")
@EnableScheduling
@EntityScan(basePackages = "com.cp.ecommerce.adapter.persistence")
@ComponentScan(basePackages = { "com.cp.ecommerce.adapter.persistence", "com.cp.ecommerce.adapter.common.resilience" })
public class PersistenceConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    /** Virtual-thread scheduler (spring.threads.virtual.enabled): database failures are logged without their message. */
    @Bean
    SimpleAsyncTaskSchedulerCustomizer scheduledTaskFailureLogging() {
        return scheduler -> scheduler.setErrorHandler(new ScheduledTaskFailureHandler());
    }

    /** Platform-thread scheduler: the same failure logging as {@link #scheduledTaskFailureLogging()}. */
    @Bean
    ThreadPoolTaskSchedulerCustomizer threadPoolScheduledTaskFailureLogging() {
        return scheduler -> scheduler.setErrorHandler(new ScheduledTaskFailureHandler());
    }

}
