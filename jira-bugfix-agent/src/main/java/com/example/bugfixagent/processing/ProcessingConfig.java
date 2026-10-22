package com.example.bugfixagent.processing;

import com.example.bugfixagent.config.AgentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
class ProcessingConfig {

    @Bean(name = "bugfixExecutor")
    ThreadPoolTaskExecutor bugfixExecutor(AgentProperties properties) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.maxConcurrentJobs());
        executor.setMaxPoolSize(properties.maxConcurrentJobs());
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("bugfix-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    @Bean
    ModeResolver modeResolver(AgentProperties properties) {
        AgentProperties.Mode mode = properties.mode();
        return new ModeResolver(mode.defaultMode(), mode.analyzeOnlyLabels(), mode.autoPrLabels(), mode.autoPrPriorities());
    }
}
