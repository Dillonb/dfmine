package com.dillonbeliveau;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Created by dillon on 7/16/16.
 */
@SpringBootApplication
@EnableScheduling
public class DFMineApplication {
    public static void main(String[] args) {
        SpringApplication.run(DFMineApplication.class, args);
    }
}
