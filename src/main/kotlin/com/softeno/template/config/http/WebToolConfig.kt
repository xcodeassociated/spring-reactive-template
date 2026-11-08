package com.softeno.template.config.http

import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * Serves the legacy repo-root `web/` directory under /web (dev-only; the
 * canonical chat tool now lives at classpath:/static/chat.html, packaged in
 * the jar and served permitAll at /chat.html). If `web/` does not exist the
 * handler just 404s.
 *
 * Note: browser login also requires the Keycloak `frontend` client to accept
 * the page's origin as a valid redirect URI (see the chat.html header).
 */
@Configuration
class WebToolConfig : WebFluxConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/web/**")
            .addResourceLocations("file:web/", "classpath:/static/web/")
    }
}
