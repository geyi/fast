package com.kuaidi100.fast.server;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class ThreadPoolUtils {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolUtils.class);

    private static volatile ThreadPoolUtils threadPoolUtils = null;
    private volatile ThreadPoolExecutor executor;
    private static final AtomicLongFieldUpdater<ThreadPoolUtils> WAITING_TIME_UPDATER =
            AtomicLongFieldUpdater.newUpdater(ThreadPoolUtils.class, "waitingTime");
    private volatile long waitingTime = 0;
    private static final AtomicLongFieldUpdater<ThreadPoolUtils> TOTAL_TIME_UPDATER =
            AtomicLongFieldUpdater.newUpdater(ThreadPoolUtils.class, "totalTime");
    private volatile long totalTime = 0;
    public static final int CORE_POOL_SIZE = 256;

    private ThreadPoolUtils() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int corePoolSize;
        int maxPoolSize;
        if (availableProcessors <= 2) {
            corePoolSize = availableProcessors << 4;
            maxPoolSize = availableProcessors << 5;
        } else {
            corePoolSize = CORE_POOL_SIZE;
            maxPoolSize = CORE_POOL_SIZE;
        }
        int queueSize = 200;
        log.info("corePoolSize: {}, maxPoolSize: {}, queueSize: {}", corePoolSize, maxPoolSize, queueSize);
        executor = new ThreadPoolExecutor(corePoolSize, maxPoolSize, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueSize),
                new ThreadFactoryBuilder().setNameFormat("fast-%d").build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public static ThreadPoolUtils getInstance() {
        if (threadPoolUtils == null) {
            synchronized (ThreadPoolUtils.class) {
                if (threadPoolUtils == null) {
                    threadPoolUtils = new ThreadPoolUtils();
                }
            }
        }
        return threadPoolUtils;
    }

    public void execute(Runnable runnable) {
        executor.execute(runnable);
    }

    public <T> Future<T> execute(Runnable runnable, T result) {
        Future<T> future = executor.submit(runnable, result);
        return future;
    }

    public Executor getExecutor() {
        return this.executor;
    }

    public void print() {
//        log.debug("activeCount: {}", executor.getActiveCount());
//        log.debug("completedTaskCount: {}", executor.getCompletedTaskCount());
//        log.debug("taskCount: {}", executor.getTaskCount());
//        log.debug("queueSize: {}", executor.getQueue().size());
        int queueSize = executor.getQueue().size();
        if (queueSize >= 10) {
            /*int coreSize = executor.getCorePoolSize() << 1;
            if (coreSize <= 16384) {
                executor.setCorePoolSize(coreSize);
            }*/
            log.warn("queueSize: {}, corePoolSize: {}", queueSize, executor.getCorePoolSize());
        }
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    public void updateWaitingTime(long time) {
        long newTime = WAITING_TIME_UPDATER.addAndGet(this, time);
        log.info("new waiting time: {}", newTime);
    }

    public void updateTotalTime(long time) {
        long newTime = TOTAL_TIME_UPDATER.addAndGet(this, time);
        log.info("new total time: {}", newTime);
    }
}
