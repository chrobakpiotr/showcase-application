package com.cp.ecommerce.adapter.amqp.configuration;

import com.cp.ecommerce.adapter.amqp.order.MessageListener;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.thread.Threading;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;

/**
 * Configuration class for AMQP messaging.
 */
@Configuration
@EnableRabbit
@ConditionalOnProperty(name = "service.rabbitmq.enabled", havingValue = "true")
public class MessagingConfiguration {

    public static final String TOPIC_EXCHANGE_NAME = "com.cp.e.topic.order";

    public static final String QUEUE_NAME = "com.cp.q.order.v1";

    public static final String ROUTING_KEY = "order.v1";

    @Bean
    Queue queue() {

        return new Queue(QUEUE_NAME, false);
    }

    @Bean
    TopicExchange exchange() {

        return new TopicExchange(TOPIC_EXCHANGE_NAME);
    }

    @Bean
    Binding binding(final Queue queue, final TopicExchange exchange) {

        return BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
    }

    /**
     * Virtual-thread executor for the listener container below, mirroring {@code
     * spring.threads.virtual.enabled} (see {@link Threading#VIRTUAL}). Unlike Boot's auto-configured Tomcat/task-executor
     * beans, this container is built by hand, so it needs its own opt-in wiring to pick up the same setting - see ADR 0011. A
     * no-op (Spring AMQP falls back to its own default executor) when the property is off.
     */
    @Bean
    @ConditionalOnThreading(Threading.VIRTUAL)
    AsyncTaskExecutor rabbitListenerTaskExecutor() {

        return new VirtualThreadTaskExecutor("rabbitmq-listener-");
    }

    /**
     * Narrowed to this configuration's own {@link #rabbitListenerTaskExecutor()} bean by {@link Qualifier}: since Boot 4 also
     * auto-configures a virtual-thread {@code taskScheduler} bean (see {@code DefaultTaskSchedulerConfiguration}) that likewise
     * implements {@link AsyncTaskExecutor}, an unqualified {@code ObjectProvider<AsyncTaskExecutor>} would find two matching
     * candidates and {@link ObjectProvider#ifAvailable} would throw {@code NoUniqueBeanDefinitionException}.
     */
    @Bean
    SimpleMessageListenerContainer container(
            final ConnectionFactory connectionFactory,
            final MessageListenerAdapter listenerAdapter,
            @Qualifier("rabbitListenerTaskExecutor") final ObjectProvider<AsyncTaskExecutor> taskExecutorProvider) {

        final SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(QUEUE_NAME);
        container.setMessageListener(listenerAdapter);
        taskExecutorProvider.ifAvailable(container::setTaskExecutor);
        return container;
    }

    @Bean
    MessageListenerAdapter listenerAdapter(final MessageListener listener) {

        return new MessageListenerAdapter(listener, "receiveMessage");
    }

}
