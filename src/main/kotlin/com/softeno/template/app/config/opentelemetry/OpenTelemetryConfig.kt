package com.softeno.template.app.config.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.support.ContextPropagatingTaskDecorator
import org.springframework.stereotype.Component


@Component
class InstallOpenTelemetryAppender(
    private val openTelemetry: OpenTelemetry
) : InitializingBean {

    override fun afterPropertiesSet() {
        OpenTelemetryAppender.install(openTelemetry)
    }
}

@Configuration(proxyBeanMethods = false)
class ContextPropagationConfiguration {
    @Bean
    fun contextPropagatingTaskDecorator(): ContextPropagatingTaskDecorator {
        return ContextPropagatingTaskDecorator()
    }
}
