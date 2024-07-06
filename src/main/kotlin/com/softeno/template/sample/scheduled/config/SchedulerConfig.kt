package com.softeno.template.sample.scheduled.config

import com.softeno.template.sample.http.internal.async.config.MDCTaskDecorator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
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
        executor.setTaskDecorator(MDCTaskDecorator())
        executor.initialize()
        return executor
    }
}
