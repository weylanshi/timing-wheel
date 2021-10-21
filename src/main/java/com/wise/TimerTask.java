package com.wise;

import lombok.Data;

/**
 * @author shiweinan
 */
@Data
public abstract class TimerTask implements Runnable {

    /**
     * timestamp in millisecond
     */
    protected Long delayMs;

    public TimerTask(Long delayMs) {
        this.delayMs = delayMs;
    }

    private TimerTaskEntry timerTaskEntry;

    public TimerTaskEntry getTimerTaskEntry() {
        return timerTaskEntry;
    }

    public void setTimerTaskEntry(TimerTaskEntry entry) {
        synchronized (this) {
            if (timerTaskEntry != null && timerTaskEntry != entry) {
                timerTaskEntry.remove();
            }
            timerTaskEntry = entry;
        }
    }

    public void cancel() {
        synchronized (this) {
            if (timerTaskEntry != null) {
                timerTaskEntry.remove();
            }
            timerTaskEntry = null;
        }
    }

}
