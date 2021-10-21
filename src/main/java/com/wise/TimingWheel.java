package com.wise;

import lombok.Data;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author shiweinan
 */
@Data
public class TimingWheel {

    /**
     * 时间轮由多个时间格组成，每个时间格就是 tickMs，它代表当前时间轮的基本时间跨度
     */
    private Long tickMs;
    /**
     * 代表每一层时间轮的格数
     */
    private Integer wheelSize;
    /**
     * 当前时间轮的总体时间跨度，interval=tickMs × wheelSize
     */
    private Long interval;
    /**
     * 构造当层时间轮时候的当前时间
     */
    private Long startMs;
    /**
     * 表示时间轮当前所处的时间，currentTime 是 tickMs 的整数倍
     */
    private Long currentTime;

    private TimerTaskList[] buckets;

    private AtomicInteger taskCounter;
    private DelayQueue<TimerTaskList> queue;

    /**
     * overflowWheel可能由两个并发线程通过add（）进行更新和读取。
     * 因此，由于JVM的双重检查锁定模式的问题，它需要是易变的
     */
    private volatile TimingWheel overflowWheel;


    public TimingWheel(Long tickMs, Integer wheelSize, Long startMs, AtomicInteger taskCounter, DelayQueue<TimerTaskList> queue) {
        this.tickMs = tickMs;
        this.wheelSize = wheelSize;
        this.interval = tickMs * wheelSize;
        this.startMs = startMs;
        this.currentTime = startMs - (startMs % tickMs);
        this.taskCounter = taskCounter;
        this.queue = queue;

        this.buckets = new TimerTaskList[wheelSize];
        for (int i = 0; i < wheelSize; i++) {
            buckets[i] = new TimerTaskList(taskCounter);
        }

    }

    public void addOverflowWheel() {
        synchronized (this) {
            if (overflowWheel == null) {
                overflowWheel = new TimingWheel(interval, wheelSize, currentTime, taskCounter, queue);
            }
        }
    }

    public Boolean add(TimerTaskEntry timerTaskEntry) {
        Long expiration = timerTaskEntry.getExpirationMs();
        if (timerTaskEntry.cancelled()) {
            // 已取消
            return Boolean.FALSE;
        } else if (expiration < currentTime + tickMs) {
            // 已过期
            return Boolean.FALSE;
        } else if (expiration < currentTime + interval) {
            // 设置到当前 bucket
            long virtualId = expiration / tickMs;
            // 找到任务对应本时间轮的bucket
            TimerTaskList bucket = buckets[(int) (virtualId % wheelSize.longValue())];
            bucket.add(timerTaskEntry);

            // 设置当前 bucket 过期时间
            // 只有本bucket内的任务都过期后才会bucket.setExpiration返回true此时将bucket放入延迟队列
            if (bucket.setExpiration(virtualId * tickMs)) {
                // bucket是一个TimerTaskList，它实现了java.util.concurrent.Delayed接口，里面是一个多任务组成的链表
                // bucket 需要排队，因为它是一个过期的 bucket
                // 我们只需要在 bucket 的过期时间发生变化时排队，即轮子已经前进，以前的 bucket 得到重用；
                // 在同一轮循环内设置过期的进一步调用将传入相同的值，因此返回 false，因此具有相同过期的 bucket 将不会多次排队。
                return queue.offer(bucket);
            }
            return Boolean.TRUE;
        } else {
            // Out of the interval. Put it into the parent timer
            //任务的过期时间不在本时间轮周期内说明需要升级时间轮，如果不存在则构造上一层时间轮，继续用上一层时间轮添加任务
            if (overflowWheel == null) {
                addOverflowWheel();
            }
            return overflowWheel.add(timerTaskEntry);
        }
    }

    /**
     * 尝试推进时间轮
     *
     * @param timeMs
     */
    public void advanceClock(Long timeMs) {
        if (timeMs >= currentTime + tickMs) {
            // 把当前时间打平为时间轮tickMs的整数倍
            currentTime = timeMs - (timeMs % tickMs);
            // 如果有溢流轮，尝试提前溢流轮的时钟
            // Try to advance the clock of the overflow wheel if present
            //驱动上层时间轮，这里的传给上层的currentTime时间是本层时间轮打平过的，但是在上层时间轮还是会继续打平
            if (overflowWheel != null) {
                overflowWheel.advanceClock(currentTime);
            }
        }
    }

}
