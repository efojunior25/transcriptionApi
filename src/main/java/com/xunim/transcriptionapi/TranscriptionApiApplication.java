package com.xunim.transcriptionapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TranscriptionApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TranscriptionApiApplication.class, args);
    }

}
