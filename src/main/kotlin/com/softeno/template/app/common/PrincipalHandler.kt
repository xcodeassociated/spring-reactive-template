package com.softeno.template.app.common

import kotlinx.coroutines.reactive.awaitSingle
import org.apache.commons.logging.Log
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import reactor.core.publisher.Mono
import java.security.Principal

interface PrincipalHandler {
    suspend fun showPrincipal(log: Log, monoPrincipal: Mono<Principal>) {
        val principal = monoPrincipal.awaitSingle()
        log.info("principal: $principal, name: ${principal.name}")
        val authentication = ReactiveSecurityContextHolder.getContext().map { it.authentication }.awaitSingle()
        val token = (authentication as JwtAuthenticationToken).token
        val userId = token.claims["sub"]
        val authorities = authentication.authorities
        log.debug("authentication: $authentication")
        log.debug("authorities: $authorities")
        log.debug("token: $token")
        log.debug("token claims: ${token.claims}")
        log.info("keycloak userId: $userId")
    }
}