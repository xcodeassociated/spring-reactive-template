package com.softeno.template.config.http

import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * Serves the repo-root `web/` directory under the /web path — a dev-only static
 * home for the browser dev tools: `web/chat.html` (ticket-auth WebSocket) and
 * `web/sse.html` (ticket-auth SSE). Path is permitAll in SecurityConfig. If `web/`
 * does not exist (production image, cleaned checkout) the handler just 404s.
 *
 * Note: browser login also requires the Keycloak `frontend` client to accept this
 * page's origin as redirect/web origin. The stock realm config allows
 * http://localhost:3000 — for quick testing serve the same directory there:
 *   (cd web && python3 -m http.server 3000)
 */
@Configuration
class WebToolConfig : WebFluxConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/web/**")
            .addResourceLocations("file:web/", "classpath:/static/web/")
    }
}
