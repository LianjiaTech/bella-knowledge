package com.ke.bella.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ke.bella.files.configuration.Configs;

public class TaskExecutorTest {
    private final CountDownLatch releaseWorkers = new CountDownLatch(1);

    @Before
    public void setUp() throws Exception {
        resetExecutor();
        Configs.TASK_THREAD_NUMS = 2;
        Configs.TASK_QUEUE_CAPACITY = 2;
    }

    @After
    public void tearDown() throws Exception {
        releaseWorkers.countDown();
        resetExecutor();
    }

    @Test
    public void queuesBurstWhenAllWorkersAreBusy() throws Exception {
        CountDownLatch workersStarted = new CountDownLatch(2);
        AtomicInteger queuedTasks = new AtomicInteger();

        CompletableFuture<Void> first = TaskExecutor.submit(() -> await(workersStarted));
        CompletableFuture<Void> second = TaskExecutor.submit(() -> await(workersStarted));
        assertTrue(workersStarted.await(5, TimeUnit.SECONDS));

        CompletableFuture<Void> third = TaskExecutor.submit(queuedTasks::incrementAndGet);
        CompletableFuture<Void> fourth = TaskExecutor.submit(queuedTasks::incrementAndGet);

        ThreadPoolExecutor executor = (ThreadPoolExecutor) executor();
        assertEquals(2, executor.getPoolSize());
        assertEquals(2, executor.getQueue().size());

        releaseWorkers.countDown();
        CompletableFuture.allOf(first, second, third, fourth).get(5, TimeUnit.SECONDS);
        assertEquals(2, queuedTasks.get());
    }

    private void await(CountDownLatch workersStarted) {
        workersStarted.countDown();
        try {
            releaseWorkers.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static ExecutorService executor() throws Exception {
        Field field = TaskExecutor.class.getDeclaredField("executor");
        field.setAccessible(true);
        return (ExecutorService) field.get(null);
    }

    private static void resetExecutor() throws Exception {
        ExecutorService current = executor();
        if(current != null) {
            current.shutdownNow();
        }
        Field field = TaskExecutor.class.getDeclaredField("executor");
        field.setAccessible(true);
        field.set(null, null);
    }
}
