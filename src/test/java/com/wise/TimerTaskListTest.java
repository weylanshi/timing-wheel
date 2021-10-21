package com.wise;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TimerTaskListTest {

    private class TestTask extends TimerTask {

        public TestTask(Long delayMs) {
            super(delayMs);
        }

        @Override
        public void run() {

        }

        @Override
        public String toString() {
            return "TestTask{}";
        }
    }

    private Integer size(TimerTaskList list) {
        AtomicReference<Integer> count = new AtomicReference<>(0);
        list.foreach(timerTask -> count.updateAndGet(v -> v + 1));
        return count.get();
    }

    @Test
    public void testAll() {
        AtomicInteger sharedCounter = new AtomicInteger(0);
        TimerTaskList list1 = new TimerTaskList(sharedCounter);
        TimerTaskList list2 = new TimerTaskList(sharedCounter);
        TimerTaskList list3 = new TimerTaskList(sharedCounter);

        List<TestTask> tasks = IntStream.rangeClosed(1, 10).boxed().map(i -> {
            TestTask task = new TestTask(0L);
            list1.add(new TimerTaskEntry(task, 10L));
            Assertions.assertEquals(i, sharedCounter.get());
            return task;
        }).collect(Collectors.toList());

        Assertions.assertEquals(tasks.size(), sharedCounter.get());

        //重新插入现有任务不应更改任务计数
        tasks.stream().limit(4).forEach(task -> {
            int prevCount = sharedCounter.get();
            list2.add(new TimerTaskEntry(task, 10L));
            Assertions.assertEquals(prevCount, sharedCounter.get());
        });

        // 重新插入现有任务不应更改任务计数
        tasks.stream().skip(4).forEach(task -> {
            int prevCont = sharedCounter.get();
            list3.add(new TimerTaskEntry(task, 10L));
            Assertions.assertEquals(prevCont, sharedCounter.get());
        });

        Assertions.assertEquals(0, size(list1));
        Assertions.assertEquals(4, size(list2));
        Assertions.assertEquals(6, size(list3));

        Assertions.assertEquals(tasks.size(), sharedCounter.get());

        // 取消 task
        list1.foreach(TimerTask::cancel);
        Assertions.assertEquals(0, size(list1));
        Assertions.assertEquals(4, size(list2));
        Assertions.assertEquals(6, size(list3));

        list2.foreach(TimerTask::cancel);
        Assertions.assertEquals(0, size(list1));
        Assertions.assertEquals(0, size(list2));
        Assertions.assertEquals(6, size(list3));

        list3.foreach(TimerTask::cancel);
        Assertions.assertEquals(0, size(list1));
        Assertions.assertEquals(0, size(list2));
        Assertions.assertEquals(0, size(list3));


    }
}
