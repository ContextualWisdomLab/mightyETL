package com.xtrmetl.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Starts the standalone mightyETL Eureka service registry.
 *
 * <p>The registry provides service discovery for deployment profiles that explicitly include
 * Eureka. Authentication and production service-identity policy are separate security controls;
 * this bootstrap class only owns starting the Spring Cloud Netflix server.</p>
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    /**
     * Launches the Eureka registry through Spring Boot.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
