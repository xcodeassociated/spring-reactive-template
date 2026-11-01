package com.softeno.template.config.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

/**
 * Unit tests for the production SecurityConfig JWT converters — they never run
 * under the integration profile (test security is permit-all), so without these
 * the realm-role → authority mapping would be untested.
 */
class SecurityJwtConvertersTest {
    private fun jwt(claims: Map<String, Any>) =
        Jwt("token-value", Instant.now().minusSeconds(10), Instant.now().plusSeconds(600), mapOf("typ" to "JWT"), claims)

    @Test
    fun `realm_access roles become authorities`() {
        val auth = SecurityConfig.AuthenticationConverter().convert(
            jwt(mapOf("sub" to "user-1", "realm_access" to mapOf("roles" to listOf("ROLE_ADMIN", "user"))))
        )

        assertEquals("user-1", auth.name)
        assertTrue(auth.authorities.any { it.authority == "ROLE_ADMIN" })
        assertTrue(auth.authorities.any { it.authority == "user" })
    }

    @Test
    fun `missing realm_access yields no authorities`() {
        val authorities = SecurityConfig.Jwt2AuthenticationConverter().convert(jwt(mapOf("sub" to "user-1")))
        assertTrue(authorities.isEmpty())
    }

    @Test
    fun `sub claim adapter keeps the subject`() {
        val claims = SecurityConfig.UsernameSubClaimAdapter().convert(mapOf("sub" to "user-1"))
        assertEquals("user-1", claims["sub"])
    }
}
