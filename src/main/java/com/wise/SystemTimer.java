package com.wise;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author shiweinan
 */
@SuppressWarnings("AlibabaThreadPoolCreation")
public class SystemTimer implements Timer {

    private String executorName;
    private Long tickMs = 1L;
    private Integer wheelSize = 20;
    private Long startMs = System.currentTimeMillis();
    private final TimingWheel timingWheel;
    private final DelayQueue<TimerTaskList> delayQueue = new DelayQueue<>();
    private final AtomicInteger taskCounter = new AtomicInteger(0);

    public SystemTimer(String executorName) {
        this.executorName = executorName;

        timingWheel = new TimingWheel(tickMs, wheelSize, startMs, taskCounter, delayQueue);
    }

    public SystemTimer(String executorName, Long tickMs, Integer wheelSize) {
        this.executorName = executorName;
        this.tickMs = tickMs;
        this.wheelSize = wheelSize;

        timingWheel = new TimingWheel(tickMs, wheelSize, startMs, taskCounter, delayQueue);
    }

    public SystemTimer(String executorName, Long tickMs, Integer wheelSize, Long startMs) {
        this.executorName = executorName;
        this.tickMs = tickMs;
        this.wheelSize = wheelSize;
        this.startMs = startMs;

        timingWheel = new TimingWheel(tickMs, wheelSize, startMs, taskCounter, delayQueue);
    }

    /**
     * 驱动线程池
     */
    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(1, (runnable) -> new Thread(runnable, "executor-" + executorName));

    /**
     * 用于在勾选时保护数据结构的锁
     */
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();


    @Override
    public void add(TimerTask timerTask) {
        readLock.lock();
        try {
            addTimerTaskEntry(new TimerTaskEntry(timerTask, timerTask.getDelayMs() + System.currentTimeMillis()));
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 在 Systemtimer 中添加一个任务，任务被包装为一个TimerTaskEntry
     *
     * @param timerTaskEntry timerTaskEntry
     */
    private void addTimerTaskEntry(TimerTaskEntry timerTaskEntry) {
        //先判断是否可以添加进时间轮中，如果不可以添加进去代表任务已经过期或者任务被取消，
        // 注意这里的timingWheel持有上一层时间轮的引用，所以可能存在递归调用
        if (!timingWheel.add(timerTaskEntry)) {
            // 已经过期或取消
            // 过期任务直接线程池异步执行掉
            if (!timerTaskEntry.cancelled()) {
                taskExecutor.submit(timerTaskEntry.timerTask);
            }
        }
    }

    @Override
    public Boolean advanceClock(Long timeoutMs) {
        try {
            TimerTaskList bucket = delayQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (bucket != null) {
                writeLock.lock();
                try {
                    while (bucket != null) {
                        // 驱动时间轮
                        timingWheel.advanceClock(bucket.getExpiration());
                        //循环buckek也就是任务列表，任务列表一个个继续添加进时间轮以此来升级或者降级时间轮，把过期任务找出来执行
                        bucket.flush(this::addTimerTaskEntry);
                        //这里就是从延迟队列取出bucket，bucket是有延迟时间的，取出代表该bucket过期，我们通过bucket能取到bucket包含的任务列表
                        bucket = delayQueue.poll();
                    }
                } finally {
                    writeLock.unlock();
                }
                return Boolean.TRUE;
            } else {
                return Boolean.FALSE;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return Boolean.FALSE;
        }
    }

    @Override
    public Integer size() {
        return taskCounter.get();
    }

    @Override
    public void shutdown() {
        taskExecutor.shutdown();
    }
}
