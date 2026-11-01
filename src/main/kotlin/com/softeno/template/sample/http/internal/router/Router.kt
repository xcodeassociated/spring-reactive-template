package com.softeno.template.sample.http.internal.router

import com.softeno.template.sample.http.internal.SampleResponseDto
import com.softeno.template.sample.http.internal.SampleService
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.slf4j.MDCContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RequestPredicates.*
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions.route
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.body

/**
 * OAuth2-secured sample resource via functional endpoints (RouterFunctions).
 * The router API itself is reactive by nature (returns `Mono<ServerResponse>`);
 * the suspend service calls are bridged with `mono { }`. `MDCContext()` is passed
 * explicitly so the caller thread's MDC (traceId/spanId) propagates into the
 * coroutine — the bridge does not capture thread locals on its own.
 */
@Configuration
class RoutesConfig {
    @Bean
    fun routes(service: SampleService): RouterFunction<ServerResponse> {
        return route(GET("/sample-secured/{id}")) { req ->
            ok().body(mono(MDCContext()) { service.getHandler(req.pathVariable("id")) })
        }.andRoute(POST("/sample-secured")) { req ->
            req.bodyToMono(SampleResponseDto::class.java).flatMap { dto ->
                ok().body(mono(MDCContext()) { service.postHandler(dto) })
            }
        }.andRoute(PUT("/sample-secured/{id}")) { req ->
            req.bodyToMono(SampleResponseDto::class.java).flatMap { dto ->
                ok().body(mono(MDCContext()) { service.putHandler(req.pathVariable("id"), dto) })
            }
        }.andRoute(DELETE("/sample-secured/{id}")) { req ->
            ok().build(mono(MDCContext()) { service.deleteHandler(req.pathVariable("id")) }.then())
        }
    }
}
