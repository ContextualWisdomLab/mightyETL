package com.xtrmetl.etl;

import com.xtrmetl.etl.connector.ConnectorProperties;
import com.xtrmetl.etl.job.EtlJobProperties;
import com.xtrmetl.etl.service.EtlBatchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bootstraps the mightyETL transformation and loading service.
 */
@SpringBootApplication(scanBasePackages = {"com.xtrmetl.etl", "com.xtrmetl.common"})
@EnableDiscoveryClient
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableRetry
@EnableScheduling
@EnableConfigurationProperties({
        ConnectorProperties.class,
        EtlBatchProperties.class,
        EtlJobProperties.class
})
public class EtlApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(EtlApplication.class, args);
    }
}
