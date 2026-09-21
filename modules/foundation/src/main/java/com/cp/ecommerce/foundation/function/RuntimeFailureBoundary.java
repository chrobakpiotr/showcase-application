package com.cp.ecommerce.foundation.function;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Executes an unchecked action synchronously and exposes only its runtime failure to an explicit handler.
 *
 * <p>
 * This is intended for best-effort/recovery boundaries that deliberately continue after any {@link RuntimeException}. The
 * action runs on the calling thread. {@link Error}s are rethrown unchanged and failures raised by the handler itself propagate
 * unchanged.
 * </p>
 */
public final class RuntimeFailureBoundary {

    private RuntimeFailureBoundary() {
    }

    public static void run(final Runnable action, final Consumer<RuntimeException> onFailure) {

        call(() -> {
            action.run();
            return null;
        }, exception -> {
            onFailure.accept(exception);
            return null;
        });
    }

    public static <T> T call(final Supplier<T> action, final Function<RuntimeException, T> onFailure) {

        final FutureTask<T> task = new FutureTask<>(action::get);
        task.run();

        try {
            return task.get();
        } catch (final ExecutionException exception) {
            return handleFailure(exception.getCause(), onFailure);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while observing synchronous runtime action", exception);
        }
    }

    private static <T> T handleFailure(final Throwable failure, final Function<RuntimeException, T> onFailure) {

        if (failure instanceof RuntimeException runtimeException) {
            return onFailure.apply(runtimeException);
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Unexpected checked failure from runtime action", failure);
    }
}
