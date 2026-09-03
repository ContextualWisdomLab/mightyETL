package com.xtrmetl.cdc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Starts the standalone mightyETL CDC service with service discovery and Kafka support during CDC bootstrap.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
@org.springframework.boot.context.properties.EnableConfigurationProperties(com.xtrmetl.cdc.config.XtrmetlProperties.class)
public class CdcApplication {

    /**
     * Launches the CDC service through Spring Boot.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(CdcApplication.class, args);
    }
}
