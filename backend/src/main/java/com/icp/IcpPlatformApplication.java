package com.icp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class IcpPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(IcpPlatformApplication.class, args);
    }
}
