package com.ghostbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The single executable entry point of GhostBridge: the composition root for the shared Spring Boot infrastructure, the AI
 * core and the App use cases. Default scanning starts at {@code com.ghostbridge}; nothing here is configured in code.
 */
@SpringBootApplication
public class GhostBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GhostBridgeApplication.class, args);
    }
}
