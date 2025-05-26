package com.roc.netty.app.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;

/**
 * @Description
 * @Author: Zhang Peng
 * @Date: 2025/5/26
 */

@Slf4j
@RestController
public class CounterController {
    private int counter = 0;

    @GetMapping("/increment")
    public void increment() throws InterruptedException {
        while (true) {
            // 获取并打印计数
            System.out.println("Counter Increment: " + counter++);
            log.info("Counter Increment: " + counter++);
            Thread.sleep(1000);
        }
    }
}
