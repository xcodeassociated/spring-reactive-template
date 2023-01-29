package com.softeno.template.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import reactor.core.publisher.Mono
import java.util.*


@Profile(value = ["!test", "!integration"])
@EnableWebFluxSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
class SecurityConfig {

    class Jwt2AuthenticationConverter : Converter<Jwt, Collection<GrantedAuthority>> {
        override fun convert(jwt: Jwt): Collection<GrantedAuthority> {
            val realmAccess = jwt.claims.getOrDefault("realm_access", mapOf<String, Any>()) as Map<String, Any>
            val realmRoles = (realmAccess["roles"] ?: listOf<String>()) as Collection<String>

            return realmRoles
                .map { role: String -> SimpleGrantedAuthority(role) }.toList()
        }

    }

    class AuthenticationConverter:  Converter<Jwt, AbstractAuthenticationToken> {
        override fun convert(jwt: Jwt): AbstractAuthenticationToken {
            return JwtAuthenticationToken(jwt, Jwt2AuthenticationConverter().convert(jwt))
        }

    }

    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = listOf("*")
        configuration.allowedMethods = listOf("*")
        configuration.allowedHeaders = listOf("*")
        configuration.exposedHeaders = listOf("*")

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        // note: swagger can be restricted by cors
        return source
    }

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .cors().configurationSource(corsConfigurationSource()).and()
            .csrf().disable()
            .authorizeExchange { authExchange ->
                authExchange.pathMatchers(
                    "/async/**",
                    "/sample/**",
                    "/external/**",
                    "/rsocket/**",
                    "/",
                    "/swagger-ui.html",
                    "/webjars/**",
                    "/swagger-resources/**",
                    "/v2/api-docs")
                    .permitAll()
                    .pathMatchers("/reactive/**", "/coroutine/**", "/ws/**" ).hasAuthority("ROLE_ADMIN")
                    .pathMatchers("/sample-secured/**").authenticated()
            }
            .oauth2ResourceServer {
                it.jwt().jwtDecoder(jwtDecoder())
                it.jwt().jwtAuthenticationConverter {
                    jwt -> Mono.just(AuthenticationConverter().convert(jwt))
                }
            }
            .build()
    }
}

class UsernameSubClaimAdapter : Converter<Map<String, Any>, Map<String, Any>> {
    private val delegate = MappedJwtClaimSetConverter.withDefaults(Collections.emptyMap())
    override fun convert(claims: Map<String, Any>): Map<String, Any> {
        val convertedClaims = delegate.convert(claims)
        val username = convertedClaims?.get("sub") as String
        convertedClaims["sub"] = username
        return convertedClaims
    }
}
@Bean
fun jwtDecoder(): ReactiveJwtDecoder {
    val jwtDecoder: NimbusReactiveJwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri("http://localhost:8090/realms/master/protocol/openid-connect/certs").build()
    jwtDecoder.setClaimSetConverter(UsernameSubClaimAdapter())
    jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("http://localhost:8090/realms/master"))
    return jwtDecoder
}