package com.softeno.template.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder
import org.springframework.cloud.client.circuitbreaker.Customizer
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.cors.reactive.CorsUtils
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Duration


@Configuration
class CorsConfig {
    @Bean
    fun corsFilter(): WebFilter {
        return WebFilter { ctx: ServerWebExchange, chain: WebFilterChain ->
            val request: ServerHttpRequest = ctx.request
            if (CorsUtils.isCorsRequest(request)) {
                val response: ServerHttpResponse = ctx.response
                val headers: HttpHeaders = response.headers
                headers.add("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
                headers.add("Access-Control-Allow-Methods", ALLOWED_METHODS)
                headers.add("Access-Control-Max-Age", MAX_AGE)
                headers.add("Access-Control-Allow-Headers", ALLOWED_HEADERS)
                if (request.method === HttpMethod.OPTIONS) {
                    response.statusCode = HttpStatus.OK
                    return@WebFilter Mono.empty<Void?>()
                }
            }
            chain.filter(ctx)
        }
    }

    companion object {
        private const val ALLOWED_HEADERS =
            "x-requested-with, authorization, Content-Type, Authorization, credential, X-XSRF-TOKEN"
        private const val ALLOWED_METHODS = "GET, PUT, POST, DELETE, OPTIONS"
        private const val ALLOWED_ORIGIN = "*"
        private const val MAX_AGE = "3600"
    }
}

@Configuration
class ReactiveCircuitBreakerConfig {
    @Bean
    fun reactiveResilience4JCircuitBreakerFactory(): ReactiveCircuitBreakerFactory<*, *> {
        return ReactiveResilience4JCircuitBreakerFactory()
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