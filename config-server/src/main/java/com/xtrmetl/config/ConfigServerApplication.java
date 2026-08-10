package com.xtrmetl.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Starts the standalone mightyETL Config Server.
 *
 * <p>This bootstrap owns only starting the Spring Cloud Config server. Deployment-owned Git
 * repository selection is a separate authority hardened by #179/#189, and inbound client
 * authentication remains the separate security decision tracked by #193. The existence of this
 * module does not by itself make Config Server a required/default mightyETL deployment service.</p>
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    /**
     * Launches the Config Server through Spring Boot.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
