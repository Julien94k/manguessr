package com.manguessr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class ManguessrApplication {

    public static void main(String[] args) {
        SpringApplication.run(ManguessrApplication.class, args);
    }
}
