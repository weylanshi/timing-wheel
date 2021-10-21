package com.wise;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;


/**
 * @author shiweinan
 */
@Getter
@Setter
public class TimerTaskEntry implements Comparable<TimerTaskEntry> {

    protected TimerTask timerTask;
    private Long expirationMs;

    protected volatile TimerTaskList list;
    protected TimerTaskEntry next;
    protected TimerTaskEntry prev;


    public TimerTaskEntry(TimerTask timerTask, Long expirationMs) {
        this.timerTask = timerTask;
        this.expirationMs = expirationMs;
        //如果此timerTask已由现有的计时器任务条目保留，
        // 则setTimerTaskEntry将删除它。
        if (timerTask != null) {
            timerTask.setTimerTaskEntry(this);
        }
    }

    public Boolean cancelled() {
        return timerTask.getTimerTaskEntry() != this;
    }

    public void remove() {
        TimerTaskList currentList = this.list;
        // 如果在另一个线程将条目从一个任务条目列表移动到另一个任务条目列表时调用remove，
        // 则由于list的值发生更改，该操作可能无法删除该条目。因此，重试，直到列表变为空。
        // 在极少数情况下，此线程会看到null并退出循环，但另一个线程稍后会将条目插入另一个列表。
        while (currentList != null) {
            currentList.remove(this);
            currentList = list;
        }
    }


    @Override
    public int compareTo(TimerTaskEntry that) {
        return Long.compare(expirationMs, that.expirationMs);
    }


}
