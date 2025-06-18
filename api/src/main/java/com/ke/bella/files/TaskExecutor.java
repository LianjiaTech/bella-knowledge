package com.ke.bella.files;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.RandomUtils;

import com.ke.bella.files.configuration.Configs;
import com.ke.bella.openapi.BellaContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskExecutor {
    private static ThreadFactory tf = new NamedThreadFactory("bella-worker-", true);

    private static volatile ExecutorService executor;
    private static volatile ScheduledExecutorService scheduledExecutor;

    private static synchronized ExecutorService getExecutor() {
        if(executor == null) {
            executor = new ThreadPoolExecutor(
                    Configs.TASK_THREAD_NUMS,
                    Configs.TASK_THREAD_NUMS,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new SynchronousQueue<>(),
                    tf);
        }
        return executor;
    }

    private static synchronized ScheduledExecutorService getScheduledExecutor() {
        if(scheduledExecutor == null) {
            scheduledExecutor = Executors.newScheduledThreadPool(100, tf);
        }
        return scheduledExecutor;
    }

    public static void scheduleAtFixedRate(Runnable r, int period) {
        int initialDelay = period + RandomUtils.nextInt(1, period);
        getScheduledExecutor().scheduleAtFixedRate(r, initialDelay, period, TimeUnit.SECONDS);
    }

    public static CompletableFuture<Void> submit(Runnable r) {
        return CompletableFuture.runAsync(new Task(r), getExecutor());
    }

    public static class Task implements Runnable {
        Runnable r;
        Map<String, Object> context;

        public Task(Runnable r) {
            this.r = r;
            this.context = com.ke.bella.openapi.BellaContext.snapshot();
        }

        @Override
        public void run() {
            com.ke.bella.openapi.BellaContext.replace(context);
            try {
                r.run();
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
                if(!(e instanceof RuntimeException)) {
                    e = new RuntimeException(e);
                }
                throw (RuntimeException) e;
            } finally {
                BellaContext.clearAll();
            }
        }
    }

    public static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final boolean isDaemon;
        private final Thread.UncaughtExceptionHandler handler;

        public NamedThreadFactory(String prefix, boolean isDaemon) {
            this(prefix, isDaemon, null);
        }

        public NamedThreadFactory(String prefix, boolean isDaemon, Thread.UncaughtExceptionHandler handler) {
            this.prefix = prefix;
            this.isDaemon = isDaemon;
            this.handler = handler;
        }

        @Override
        public Thread newThread(Runnable r) {
            final Thread t = new Thread(r, String.format("%s%d", prefix, threadNumber.getAndIncrement()));
            t.setDaemon(isDaemon);
            if(this.handler != null) {
                t.setUncaughtExceptionHandler(handler);
            }
            return t;
        }
    }
}
