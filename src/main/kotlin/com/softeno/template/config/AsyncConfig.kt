package com.softeno.template.config

import org.apache.commons.logging.LogFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.lang.reflect.Method
import java.util.concurrent.Executor


@Configuration
class AsyncExecutorConfig {
    @Bean(value = ["asyncExecutor"])
    fun asyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.maxPoolSize = 24
        executor.corePoolSize = 8
        executor.setThreadNamePrefix("CUSTOM_SPRING_EXECUTOR-")
        executor.initialize()
        return executor
    }
}

class AsyncExceptionHandler: AsyncUncaughtExceptionHandler {
    private val log = LogFactory.getLog(javaClass)

    override fun handleUncaughtException(ex: Throwable, method: Method, vararg params: Any) {
        log.error("[async]: Unexpected asynchronous exception at: ${method.declaringClass.name}.${method.name}, $ex")
        // clean up here ...
    }
}

@Configuration
@EnableAsync
class AsyncConfig: AsyncConfigurer {
    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
        return AsyncExceptionHandler()
    }
}