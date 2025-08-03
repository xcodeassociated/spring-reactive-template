package com.softeno.template.app.user.notification

import kotlinx.coroutines.flow.Flow
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux


@RestController
@RequestMapping("/update/")
@Validated
class UpdateController(
    val reactiveEmitter: ReactiveUserUpdateEmitter,
    val coroutineEmitter: CoroutineUserUpdateEmitter,
) {

    @GetMapping("/user", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(response: ServerHttpResponse): Flow<ServerSentEvent<String>> {
        response.headers.apply {
            set("Cache-Control", "no-cache, no-store, must-revalidate")
            set("Connection", "keep-alive")
            set("X-Accel-Buffering", "no")
        }
        return coroutineEmitter.getFlow()
    }

    @GetMapping("/user/reactive", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun getUserUpdate(response: ServerHttpResponse): Flux<ServerSentEvent<String>> {
        response.headers.apply {
            set("Cache-Control", "no-cache, no-store, must-revalidate")
            set("Connection", "keep-alive")
            set("X-Accel-Buffering", "no")
        }
        return reactiveEmitter.getSink()
    }
}
