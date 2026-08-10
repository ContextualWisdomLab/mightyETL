package com.xtrmetl.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Starts the standalone mightyETL gateway with service discovery during gateway bootstrap.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ZuulGatewayApplication {

    /**
     * Launches the gateway through Spring Boot.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(ZuulGatewayApplication.class, args);
    }
}
