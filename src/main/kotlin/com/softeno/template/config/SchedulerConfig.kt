package com.softeno.template.config

import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.stereotype.Service
import java.util.concurrent.Executor

@Configuration
@EnableScheduling
class SchedulerExecutorConfig {
    @Bean(value = ["scheduledExecutor"])
    fun taskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.maxPoolSize = 24
        executor.corePoolSize = 8
        executor.setThreadNamePrefix("CUSTOM_SPRING_SCHEDULER-")
        executor.initialize()
        return executor
    }
}
