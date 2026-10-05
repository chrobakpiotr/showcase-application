package com.cp.ecommerce.adapter.common.utils;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** Captures one logger's events for privacy-focused adapter tests. */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;

    private final ListAppender<ILoggingEvent> appender;

    public LogCapture(final Class<?> source) {
        logger = (Logger) LoggerFactory.getLogger(source);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    public String formattedMessages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
