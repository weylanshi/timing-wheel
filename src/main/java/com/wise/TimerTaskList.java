package com.wise;


import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * @author shiweinan
 */
public class TimerTaskList implements Delayed {

    private final TimerTaskEntry root = new TimerTaskEntry(null, -1L);

    {
        root.next = root;
        root.prev = root;
    }

    private final AtomicInteger taskCounter;

    private final AtomicLong expiration = new AtomicLong(-1L);

    public TimerTaskList(AtomicInteger taskCounter) {
        this.taskCounter = taskCounter;
    }

    public Boolean setExpiration(Long expirationMs) {
        return expiration.getAndSet(expirationMs) != expirationMs;
    }

    public Long getExpiration() {
        return expiration.get();
    }

    /**
     * Apply the supplied function to each of tasks in this list
     */
    public void foreach(Consumer<TimerTask> f) {
        synchronized (this) {
            TimerTaskEntry entry = root.next;
            while (entry != root) {
                TimerTaskEntry nextEntry = entry.next;
                if (!entry.cancelled()) {
                    f.accept(entry.timerTask);
                }
                entry = nextEntry;
            }
        }
    }

    /**
     * Add a timer task entry to this list
     */
    public void add(TimerTaskEntry timerTaskEntry) {
        boolean done = Boolean.FALSE;
        while (!done) {
            // 如果计时器任务条目已经在任何其他列表中，删除它。
            // 在下面的同步块之外执行此操作，以避免死锁。
            // 重试，直到timerTaskEntry.list变为空。
            timerTaskEntry.remove();

            synchronized (this) {
                if (timerTaskEntry.list == null) {
                    // 将计时器任务条目放在列表的末尾。（root.prev指向尾部）
                    TimerTaskEntry tail = root.prev;
                    timerTaskEntry.next = root;
                    timerTaskEntry.prev = tail;
                    timerTaskEntry.list = this;
                    tail.next = timerTaskEntry;
                    root.prev = timerTaskEntry;
                    taskCounter.incrementAndGet();
                    done = true;
                }
            }
        }

    }

    /**
     * Remove the specified timer task entry from this list
     */
    public void remove(TimerTaskEntry timerTaskEntry) {
        synchronized (this) {
            if (timerTaskEntry.list.equals(this)) {
                timerTaskEntry.next.prev = timerTaskEntry.prev;
                timerTaskEntry.prev.next = timerTaskEntry.next;
                timerTaskEntry.next = null;
                timerTaskEntry.prev = null;
                timerTaskEntry.list = null;
                taskCounter.decrementAndGet();
            }
        }
    }

    /**
     * Remove all task entries and apply the supplied function to each of them
     *
     * @param f function
     */
    public void flush(Consumer<TimerTaskEntry> f) {
        synchronized (this) {
            TimerTaskEntry head = root.next;
            while (head != root) {
                remove(head);
                f.accept(head);
                head = root.next;
            }
            expiration.set(-1L);
        }
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(Math.max(getExpiration() - System.currentTimeMillis(), 0), TimeUnit.MILLISECONDS);
    }


    @Override
    public int compareTo(Delayed other) {
        if (other instanceof TimerTaskList) {
            return Long.compare(getExpiration(), ((TimerTaskList) other).getExpiration());
        }
        return 0;
    }
}




