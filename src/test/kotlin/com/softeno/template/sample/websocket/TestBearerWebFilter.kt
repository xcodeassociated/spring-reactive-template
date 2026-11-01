package com.softeno.template.sample.websocket

import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * Integration-test stand-in for the JWT resource server: when (and only when) a
 * request carries `X-Test-Sub`, it puts a JwtAuthenticationToken for that subject
 * into the security context — simulating a validated bearer without needing
 * Keycloak/Testcontainers. `X-Test-Exp-Seconds` controls the token ceiling
 * (for reauth/sweeper fixtures); `X-Test-Roles` the authorities.
 *
 * Has zero effect on requests that do not send the header, so existing
 * integration tests are unaffected.
 */
@Profile("integration")
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
class TestBearerWebFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val sub = exchange.request.headers.getFirst("X-Test-Sub")?.takeIf { it.isNotBlank() }
            ?: return chain.filter(exchange)

        val expSeconds = exchange.request.headers.getFirst("X-Test-Exp-Seconds")?.toLongOrNull() ?: 300
        val roles = exchange.request.headers.getFirst("X-Test-Roles")
            ?.split(",")?.filter { it.isNotBlank() } ?: listOf("ROLE_USER")

        val expiresAt = Instant.now().plusSeconds(expSeconds)
        // Jwt requires issuedAt <= expiresAt; keep issuedAt in the past so
        // short-lived/negative fixtures (expired-token tests) still construct.
        val issuedAt = if (expiresAt.isAfter(Instant.now())) Instant.now().minusSeconds(1) else expiresAt.minusSeconds(30)
        val jwt = Jwt(
            "test-access-token", issuedAt, expiresAt, mapOf("typ" to "JWT"),
            mapOf("sub" to sub, "sid" to "test-sid", "exp" to expiresAt.epochSecond),
        )
        val authentication = JwtAuthenticationToken(jwt, roles.map(::SimpleGrantedAuthority))
        return chain.filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
    }
}
