package com.softeno.template.sample.http.external.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import io.github.resilience4j.timelimiter.TimeLimiterRegistry
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder
import org.springframework.cloud.client.circuitbreaker.Customizer
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration


@Configuration
class ReactiveCircuitBreakerConfig {
    @Bean
    fun reactiveResilience4JCircuitBreakerFactory(): ReactiveCircuitBreakerFactory<*, *> {
        val circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults()
        val timeLimiterRegistry = TimeLimiterRegistry.ofDefaults()
        return ReactiveResilience4JCircuitBreakerFactory(circuitBreakerRegistry, timeLimiterRegistry, null,null)
    }

    @Bean
    fun defaultCustomizer(): Customizer<ReactiveResilience4JCircuitBreakerFactory> {
        return Customizer { factory: ReactiveResilience4JCircuitBreakerFactory ->
            factory.configureDefault { id: String ->
                Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                    .timeLimiterConfig(
                        TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(3)).build()
                    )
                    .build()
            }
        }
    }

}