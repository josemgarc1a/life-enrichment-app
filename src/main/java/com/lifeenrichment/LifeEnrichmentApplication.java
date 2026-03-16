package com.lifeenrichment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LifeEnrichmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(LifeEnrichmentApplication.class, args);
    }
}
