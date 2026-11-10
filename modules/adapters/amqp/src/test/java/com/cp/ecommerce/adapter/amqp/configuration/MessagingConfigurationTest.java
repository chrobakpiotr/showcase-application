package com.cp.ecommerce.adapter.amqp.configuration;

import com.cp.ecommerce.adapter.amqp.order.MessageListener;

import org.junit.jupiter.api.Test;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MessagingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MessagingConfiguration.class)
            .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
            .withBean(MessageListener.class, () -> mock(MessageListener.class));

    @Test
    void shouldKeepConsumerAbsentWhenPublishingIsEnabled() {

        contextRunner.withPropertyValues("service.rabbitmq.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SimpleMessageListenerContainer.class);
            assertThat(context).hasBean("queue").hasBean("exchange").hasBean("binding");
        });
    }

    @Test
    void shouldKeepConsumerAbsentWhenExplicitlyDisabled() {

        contextRunner.withPropertyValues("service.rabbitmq.enabled=true", "service.rabbitmq.consumer.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SimpleMessageListenerContainer.class));
    }

    @Test
    void shouldRequirePublishingConfigurationForConsumer() {

        contextRunner.withPropertyValues("service.rabbitmq.enabled=false", "service.rabbitmq.consumer.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(SimpleMessageListenerContainer.class));
    }

    @Test
    void shouldCreateConsumerForExplicitIsolatedOptIn() {

        contextRunner.withBean(BeanPostProcessor.class, () -> new BeanPostProcessor() {

            @Override
            public Object postProcessBeforeInitialization(final Object bean, final String beanName) {

                if (bean instanceof SimpleMessageListenerContainer container) {
                    // The synthetic connection must never be used for broker I/O.
                    container.setAutoStartup(false);
                }
                return bean;
            }
        }).withPropertyValues("service.rabbitmq.enabled=true", "service.rabbitmq.consumer.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SimpleMessageListenerContainer.class);
            assertThat(context.getBean(SimpleMessageListenerContainer.class).getQueueNames())
                    .containsExactly(MessagingConfiguration.QUEUE_NAME);
        });
    }
}
