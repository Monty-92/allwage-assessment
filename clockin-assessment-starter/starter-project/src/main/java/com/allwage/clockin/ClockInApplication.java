package com.allwage.clockin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClockInApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClockInApplication.class, args);
    }
}
