package com.example.bugfixagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BugfixAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(BugfixAgentApplication.class, args);
    }
}
