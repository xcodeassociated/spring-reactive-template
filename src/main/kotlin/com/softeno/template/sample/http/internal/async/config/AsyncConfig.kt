package com.softeno.template.sample.http.internal.async.config

import org.apache.commons.logging.LogFactory
import org.slf4j.MDC
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.lang.reflect.Method
import java.util.concurrent.Executor


class MDCTaskDecorator : TaskDecorator {
    override fun decorate(runnable: Runnable): Runnable {
        val contextMap = MDC.getCopyOfContextMap()
        return Runnable {
            try {
                MDC.setContextMap(contextMap)
                runnable.run()
            } finally {
                MDC.clear()
            }
        }
    }
}


@Configuration
@EnableAsync
class AsyncExecutorConfig {
    @Bean(value = ["asyncExecutor"])
    fun asyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.maxPoolSize = 24
        executor.corePoolSize = 8
        executor.setThreadNamePrefix("CUSTOM_SPRING_EXECUTOR-")
        executor.setTaskDecorator(MDCTaskDecorator())
        executor.initialize()
        return executor
    }
}

class AsyncExceptionHandler : AsyncUncaughtExceptionHandler {
    private val log = LogFactory.getLog(javaClass)

    override fun handleUncaughtException(ex: Throwable, method: Method, vararg params: Any) {
        log.error("[async]: Unexpected asynchronous exception at: ${method.declaringClass.name}.${method.name}, $ex")
        // clean up here ...
    }
}

@Configuration
class AsyncConfig : AsyncConfigurer {
    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
        return AsyncExceptionHandler()
    }
}