package com.cp.ecommerce.foundation.function;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;

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

        final FutureTask<Void> task = new FutureTask<>(() -> {
            action.run();
            return null;
        });
        task.run();

        try {
            task.get();
        } catch (final ExecutionException exception) {
            handleFailure(exception.getCause(), onFailure);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while observing synchronous runtime action", exception);
        }
    }

    private static void handleFailure(final Throwable failure, final Consumer<RuntimeException> onFailure) {

        if (failure instanceof RuntimeException runtimeException) {
            onFailure.accept(runtimeException);
            return;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Unexpected checked failure from runtime action", failure);
    }
}
