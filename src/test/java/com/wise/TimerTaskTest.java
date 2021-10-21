package com.wise;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;

public class TimerTaskTest {


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
    public void testDelayTask() throws IOException {
        System.out.println("提交任务:" + LocalDateTime.now());
        timer.add(new TimerTask(8000L) {
            @Override
            public void run() {
                System.out.println("执行任务:" + LocalDateTime.now() + "," + Thread.currentThread().getName());
            }
        });


        for (; ; ) {
            timer.advanceClock(200L);
        }


    }
}
