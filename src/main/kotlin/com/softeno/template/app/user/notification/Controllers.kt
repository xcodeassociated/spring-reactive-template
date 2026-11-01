package com.softeno.template.app.user.notification

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/update/")
@Validated
class UpdateController(
    val coroutineEmitter: CoroutineUserUpdateEmitter,
) {

    /**
     * SSE stream. Identity is bound here from the authenticated principal —
     * either a real bearer JWT or a redeemed one-time ticket (SseTicketRedeemFilter
     * wrote it into the security context). The stream's token ceiling (`exp`)
     * travels into the registry for hard enforcement.
     */
    @GetMapping("/user", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    suspend fun stream(response: ServerHttpResponse): Flow<ServerSentEvent<String>> {
        response.headers.apply {
            set("Cache-Control", "no-cache, no-store, must-revalidate")
            set("Connection", "keep-alive")
            set("X-Accel-Buffering", "no")
        }
        val authentication = ReactiveSecurityContextHolder.getContext()
            .awaitSingleOrNull()
            ?.authentication as? JwtAuthenticationToken
        val sub = (authentication?.token?.claims?.get("sub") as? String)?.takeIf { it.isNotBlank() }
        val exp: Instant? = authentication?.token?.expiresAt
        return coroutineEmitter.subscribe(sub, exp)
    }
}
