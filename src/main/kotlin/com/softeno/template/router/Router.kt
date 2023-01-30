package com.softeno.template.router

import com.softeno.template.reactive.SampleResponseDto
import com.softeno.template.reactive.SampleService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.*
import org.springframework.web.reactive.function.server.RequestPredicates.*
import org.springframework.web.reactive.function.server.RouterFunctions.route
import org.springframework.web.reactive.function.server.ServerResponse.ok

@Configuration
class RoutesConfig {
    @Bean
    fun routes(): RouterFunction<ServerResponse> {
        return route(GET("/")) { _: ServerRequest ->
            ok().body(BodyInserters.fromObject(MessageDto(message = "hello world")))
        }
        // .and(...) <- next router function
    }

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

data class MessageDto(
    val message: String
)