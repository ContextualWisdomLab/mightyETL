package com.xtrmetl.etl;

import com.xtrmetl.etl.config.EtlProcessingProperties;
import com.xtrmetl.etl.connector.ConnectorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Boots the independently deployable mightyETL processing service.
 */
@SpringBootApplication(scanBasePackages = {"com.xtrmetl.etl", "com.xtrmetl.common"})
@EnableDiscoveryClient
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableRetry
@EnableConfigurationProperties({ConnectorProperties.class, EtlProcessingProperties.class})
public class EtlApplication {

    private EtlApplication() {
        // Application entry point only.
    }

    public static void main(String[] args) {
        SpringApplication.run(EtlApplication.class, args);
    }
}
