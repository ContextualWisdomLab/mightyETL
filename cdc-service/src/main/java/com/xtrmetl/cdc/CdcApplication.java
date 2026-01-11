package com.xtrmetl.cdc;

import com.xtrmetl.cdc.config.XtrmetlProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
@org.springframework.boot.context.properties.EnableConfigurationProperties(XtrmetlProperties.class)
public class CdcApplication {
    public static void main(String[] args) {
        SpringApplication.run(CdcApplication.class, args);
    }
}
