package com.softeno.template.sample.http.internal.router

import com.softeno.template.sample.http.dto.SampleResponseDto
import com.softeno.template.sample.http.internal.reactive.SampleService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RequestPredicates.*
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions.route
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.server.body

@Configuration
class RoutesConfig {
    // OAuth2 secured sample resource
    @Bean
    fun routes(service: SampleService): RouterFunction<ServerResponse> {
        return route(GET("/sample-secured/{id}")) { req ->
            ok().body(service.getHandler(req.pathVariable("id")))
        }.andRoute(POST("/sample-secured")) { req ->
            req.bodyToMono(SampleResponseDto::class.java).map {
                service.postHandler(it)
            }.flatMap { ok().body(it) }
        }.andRoute(PUT("/sample-secured/{id}")) { req ->
            req.bodyToMono(SampleResponseDto::class.java).map {
                service.putHandler(req.pathVariable("id"), it)
            }.flatMap { ok().body(it) }
        }.andRoute(DELETE("/sample-secured/{id}")) { req ->
            ok().body(service.deleteHandler(req.pathVariable("id")))
        }
    }
}
