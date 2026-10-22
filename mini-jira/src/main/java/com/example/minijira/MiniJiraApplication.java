package com.example.minijira;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MiniJiraApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniJiraApplication.class, args);
    }
}
