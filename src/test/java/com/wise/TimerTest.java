package com.wise;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TimerTest {

    private static class TestTask extends TimerTask {

        private final AtomicBoolean completed = new AtomicBoolean(Boolean.FALSE);
        private final CountDownLatch latch;
        final ArrayList<Integer> output;
        Integer id;


        public TestTask(Long delayMs, Integer id, CountDownLatch latch, ArrayList<Integer> output) {
            super(delayMs);
            this.latch = latch;
            this.output = output;
            this.id = id;
        }

        @Override
        public void run() {
            if (completed.compareAndSet(false, true)) {
                synchronized (output) {
                    output.add(id);
                    latch.countDown();
                }
            }
        }
    }

    private Timer timer = null;

    @BeforeEach
    public void setup() {
        timer = new SystemTimer("test", 1L, 3);
    }

    @AfterEach
    public void teardown() {
        timer.shutdown();
    }

    @Test
    public void testAlreadyExpiredTask() {

        ArrayList<Integer> output = new ArrayList<>();

        List<CountDownLatch> latches = IntStream.rangeClosed(-5, 0).boxed().map(i -> {
            CountDownLatch latch = new CountDownLatch(1);
            timer.add(new TestTask(i.longValue(), i, latch, output));
            return latch;
        }).collect(Collectors.toList());

        timer.advanceClock(0L);

        latches.forEach(latch -> {
            try {
                Assertions.assertTrue(latch.await(3, TimeUnit.SECONDS), "already expired tasks should run immediately");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        Assertions.assertEquals(IntStream.rangeClosed(-5, 0).boxed().collect(Collectors.toList()), output);

    }

    @Test
    public void testTaskExpiration() {
        ArrayList<Integer> output = new ArrayList<>();

        ArrayList<TestTask> tasks = new ArrayList<>();
        ArrayList<Integer> ids = new ArrayList<>();

        List<CountDownLatch> latches = Stream.concat(
                IntStream.rangeClosed(0, 5).boxed().map(i -> {
                    CountDownLatch latch = new CountDownLatch(1);
                    tasks.add(new TestTask(i.longValue(), i, latch, output));
                    ids.add(i);
                    return latch;
                }),
                Stream.concat(
                        IntStream.rangeClosed(10, 100).boxed().map(i -> {
                            CountDownLatch latch = new CountDownLatch(2);
                            tasks.add(new TestTask(i.longValue(), i, latch, output));
                            tasks.add(new TestTask(i.longValue(), i, latch, output));
                            ids.add(i);
                            ids.add(i);
                            return latch;
                        }),
                        IntStream.rangeClosed(100, 1000).boxed().map(i -> {
                            CountDownLatch latch = new CountDownLatch(1);
                            tasks.add(new TestTask(i.longValue(), i, latch, output));
                            ids.add(i);
                            return latch;
                        })
                )
        ).collect(Collectors.toList());

        tasks.forEach(task -> timer.add(task));

        while (timer.advanceClock(2000L)) {
        }

        latches.forEach(latch -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        Assertions.assertEquals(ids.stream().sorted().collect(Collectors.toList()), output.stream().sorted().collect(Collectors.toList()), "output should match");


    }
}
