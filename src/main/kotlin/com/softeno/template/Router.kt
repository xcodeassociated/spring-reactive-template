package com.softeno.template

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.*
import reactor.core.publisher.Mono

@Configuration
class RoutesConfig {
    @Bean
    fun routes(): RouterFunction<ServerResponse> {
        return RouterFunctions.route(RequestPredicates.GET("/")) { _: ServerRequest ->
            ServerResponse.ok().body(BodyInserters.fromObject(MessageDto(message = "hello world")))
        }
        // .and(...) <- next router function
    }

    // note: there can be other bean router function added here
}

data class MessageDto(
    val message: String
)