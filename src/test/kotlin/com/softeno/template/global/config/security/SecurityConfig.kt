package com.softeno.template.global.config.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

@EnableWebFluxSecurity
@Profile(value = ["test", "integration"])
class SecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http.authorizeExchange()
            .anyExchange().permitAll()
            .and()
            .csrf().disable()
            .cors().disable()
            .build()
    }
}