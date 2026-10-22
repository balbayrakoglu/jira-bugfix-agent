package com.example.bugfixagent.repo;

import com.example.bugfixagent.config.AgentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RepoConfig {

    @Bean
    CodeContextCollector codeContextCollector(AgentProperties properties) {
        return new CodeContextCollector(properties.repo().maxContextFiles(), properties.repo().maxContextChars());
    }

    @Bean
    CodeEditApplier codeEditApplier(AgentProperties properties) {
        return new CodeEditApplier(properties.repo().protectedPaths(), properties.repo().maxChangedFiles());
    }

    @Bean
    BuildVerifier buildVerifier(AgentProperties properties) {
        return new BuildVerifier(properties.repo().verifyCommand(), properties.repo().verifyTimeout());
    }
}
