package com.softeno.template.sample.http.internal

import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Sample demo filter: logs a sanitized request line (never the one-time `ticket`) and adds a header.
 * Request logging is off by default — enable with `com.softeno.sample.request-log=true`.
 */
@Component
class ExampleWebFilter(
    @Value("\${com.softeno.sample.request-log:false}") private val requestLog: Boolean,
) : WebFilter {
    private val log = LogFactory.getLog(javaClass)
    override fun filter(serverWebExchange: ServerWebExchange, webFilterChain: WebFilterChain): Mono<Void> {
        if (requestLog) {
            val headers = serverWebExchange.request.headers
            val uri = serverWebExchange.request.uri
            val queryParams = serverWebExchange.request.queryParams
            // never log single-use handshake tickets (or any credential) even at trace
            val safeQuery = queryParams.filterKeys { it != "ticket" }
            log.info("Incoming request uri: ${uri?.run { "$scheme://$host$port$path" }}")
            log.info("Incoming request queryParams: $safeQuery")
            log.trace("Incoming request headers: $headers")
        }

        serverWebExchange.response.getHeaders().add("custom-header", "XD")
        return webFilterChain.filter(serverWebExchange)
    }
}
