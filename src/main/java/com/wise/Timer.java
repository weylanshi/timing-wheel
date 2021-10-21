package com.wise;

/**
 * @author shiweinan
 */
public interface Timer {

    /**
     * 将新任务添加到此执行器。
     * 它将在任务延迟后执行（从提交时开始）
     *
     * @param timerTask 需要添加的任务
     */
    void add(TimerTask timerTask);

    /**
     * 驱动内部时钟，执行在经过的超时持续时间内到期的任何任务。
     *
     * @param timeoutMs 等待过期时间
     * @return 是否执行了任何任务
     */
    Boolean advanceClock(Long timeoutMs);

    /**
     * 获取挂起执行的任务数
     *
     * @return 任务数
     */
    Integer size();

    /**
     * 关闭计时器服务，保留未执行的挂起任务
     */
    void shutdown();

}
