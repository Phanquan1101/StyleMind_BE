package com.stylemind.stylist.service.impl.generation;

import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs remote-work orchestration on a bounded executor and cancels it at the caller's deadline. */
final class GenerationDeadlineExecutor {
    private final ThreadPoolExecutor executor = createExecutor();

    <T> T run(Callable<T> task, long deadline) {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        Future<T> future;
        try {
            future = executor.submit(task);
        } catch (RejectedExecutionException exception) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
        }
        try {
            return future.get(nanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        }
    }

    void close() {
        executor.shutdownNow();
    }

    private static ThreadPoolExecutor createExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "stylist-generation-remote-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(16), factory, new ThreadPoolExecutor.AbortPolicy());
    }
}
