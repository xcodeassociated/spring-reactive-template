package com.softeno.template.config.http

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public config for the `chat.html` dev tool so the page is environment-agnostic:
 * it discovers the Keycloak server URL + realm from the backend's own
 * resource-server issuer instead of hardcoding a host. Everything else
 * (backend REST + `/ws`) is derived by the page from its own origin/path.
 *
 * permitAll (see SecurityConfig): it exposes nothing secret — the issuer and
 * the `frontend` public client id are already public OIDC metadata.
 */
@RestController
class ChatToolConfigController(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") private val issuer: String,
) {
    data class ChatToolConfig(val authUrl: String, val realm: String, val clientId: String)

    @GetMapping("/chat-config")
    fun config(): ChatToolConfig {
        val idx = issuer.indexOf("/realms/")
        return if (idx > 0) {
            ChatToolConfig(
                authUrl = issuer.substring(0, idx).trimEnd('/'),
                realm = issuer.substring(idx + "/realms/".length).substringBefore('/'),
                clientId = "frontend",
            )
        } else {
            ChatToolConfig(authUrl = issuer.trimEnd('/'), realm = "app", clientId = "frontend")
        }
    }
}
