package com.softeno.template.sample.websocket.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * Serves the repo-root `chat/` directory under the /chat path — a dev-only static
 * home for the browser chat tool (`chat/chat.html`). Path is permitAll in
 * SecurityConfig. If `chat/` does not exist (production image, cleaned checkout)
 * the handler just 404s; the whole package is removable per the feature-removal
 * contract.
 *
 * Note: browser login also requires the Keycloak `frontend` client to accept this
 * page's origin as redirect/web origin. The stock realm config allows
 * http://localhost:3000 — for quick testing serve the same directory there:
 *   (cd chat && python3 -m http.server 3000)
 */
@ConditionalOnProperty(name = ["com.softeno.ws.enabled"], havingValue = "true")
@Configuration
class WsChatToolConfig : WebFluxConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/chat/**")
            .addResourceLocations("file:chat/", "classpath:/static/chat/")
    }
}
