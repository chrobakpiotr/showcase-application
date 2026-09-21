package com.cp.ecommerce.foundation.function;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeFailureBoundaryTest {

    @Test
    void shouldRunActionSynchronouslyWithoutInvokingFailureHandler() {
        final Thread caller = Thread.currentThread();
        final AtomicReference<Thread> actionThread = new AtomicReference<>();
        final AtomicBoolean handled = new AtomicBoolean();

        RuntimeFailureBoundary.run(() -> actionThread.set(Thread.currentThread()), exception -> handled.set(true));

        assertThat(actionThread.get()).isSameAs(caller);
        assertThat(handled).isFalse();
    }

    @Test
    void shouldExposeRuntimeFailureToHandler() {
        final AtomicReference<RuntimeException> handled = new AtomicReference<>();
        final IllegalStateException failure = new IllegalStateException("boom");

        RuntimeFailureBoundary.run(() -> {
            throw failure;
        }, handled::set);

        assertThat(handled.get()).isSameAs(failure);
    }

    @Test
    void shouldReturnActionResult() {

        final String result = RuntimeFailureBoundary.call(() -> "ok", exception -> "fallback");

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldReturnFallbackResultForRuntimeFailure() {

        final String result = RuntimeFailureBoundary.call(() -> {
            throw new IllegalStateException("boom");
        }, exception -> "fallback:" + exception.getMessage());

        assertThat(result).isEqualTo("fallback:boom");
    }

    @Test
    void shouldPreserveFailureRaisedByHandler() {
        final IllegalArgumentException handlerFailure = new IllegalArgumentException("handler");

        assertThatThrownBy(() -> RuntimeFailureBoundary.run(() -> {
            throw new IllegalStateException("action");
        }, exception -> {
            throw handlerFailure;
        })).isSameAs(handlerFailure);
    }

    @Test
    void shouldPreserveErrorsFromAction() {
        final AssertionError error = new AssertionError("fatal");

        assertThatThrownBy(() -> RuntimeFailureBoundary.run(() -> {
            throw error;
        }, exception -> {
            throw new AssertionError("handler must not run");
        })).isSameAs(error);
    }
}
