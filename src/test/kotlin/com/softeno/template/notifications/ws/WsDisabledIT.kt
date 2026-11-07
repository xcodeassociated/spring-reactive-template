package com.softeno.template.notifications.ws

import com.softeno.template.notifications.Notification
import com.softeno.template.notifications.sse.SseNotification
import com.softeno.template.notifications.ws.api.WsTicketController
import com.softeno.template.notifications.ws.security.WsTicketRedeemFilter
import com.softeno.template.testsupport.BaseIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource

/**
 * The OFF state of `com.softeno.ws.enabled` (ADR-0003 optional-feature
 * contract): the entire WebSocket functionality — handler + mapping, message
 * service, ticket mint endpoint, redeem filter, Notification adapter — must be
 * absent, NO connection can be established, and everything else (SSE, Kafka
 * listeners driving the remaining port) must keep working.
 */
@TestPropertySource(properties = ["com.softeno.ws.enabled=false"])
class WsDisabledIT : BaseIntegrationTest() {

    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun `no WebSocket beans are registered when the feature flag is off`() {
        for (type in listOf(
            CoroutineWebSocketConfig::class.java,
            CoroutineMessageService::class.java,
            WsTicketController::class.java,
            WsNotification::class.java,
            WsTicketRedeemFilter::class.java,
        )) {
            assertTrue(
                context.getBeanNamesForType(type).isEmpty(),
                "expected no ${type.simpleName} bean with ws disabled"
            )
        }
    }

    @Test
    fun `the ws-ticket endpoint does not exist when disabled`() {
        // not 401/403 — the controller itself is gone: no mapping at all
        webTestClient.post().uri("/ws-ticket")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `no WebSocket connection can be established when disabled`() {
        // the upgrade request cannot complete: the handler mapping is absent,
        // so even a well-formed handshake attempt 404s instead of switching
        // protocols (a 101/200 here would mean a live socket — the bug this
        // test exists to catch)
        val response = webTestClient.get().uri("/ws")
            .header("Upgrade", "websocket")
            .header("Connection", "Upgrade")
            .header("Sec-WebSocket-Version", "13")
            .header("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
            .exchange()
            .returnResult(String::class.java)
        assertEquals(404, response.status.value(), "ws upgrade must not succeed while disabled")
    }

    @Test
    fun `only the SSE adapter remains on the Notification port and still serves the stream`() {
        val adapters = context.getBeanNamesForType(Notification::class.java)
        assertEquals(1, adapters.size, "expected exactly the SSE adapter, got: ${adapters.toList()}")
        assertTrue(context.getBeanNamesForType(Notification::class.java).any {
            context.getBean(it) is SseNotification
        })

        webTestClient.get().uri("/update/user")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
    }
}
