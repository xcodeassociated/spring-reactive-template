package com.softeno.template.notifications.kafka

import com.softeno.template.notifications.Notification
import com.softeno.template.testsupport.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

/**
 * Unit tests for the Keycloak-events listener: LOGOUT revokes the user across
 * every [Notification] adapter (ws, sse — mocked as port implementations),
 * LOGIN/REGISTER do not. Event payloads come from [KeycloakEventFixture] with
 * [R]-randomized subjects and ids.
 */
class KafkaKeycloakControllerTest : KeycloakEventFixture {

    private fun stubReadValue(objectMapper: ObjectMapper, type: String, userId: String, id: String) {
        every { objectMapper.readValue(any<String>(), KeycloakUserEvent::class.java) } returns
            KeycloakUserEvent(
                id = id, time = 1, type = KeycloakEventType.valueOf(type),
                realmId = "app", clientId = "frontend", userId = userId, sessionId = "s1",
                ipAddress = "127.0.0.1", error = null, details = emptyMap(),
            )
    }

    @Test
    fun `LOGOUT event revokes the user on every adapter`() {
        val userId = R.sub("logout-target")
        val eventId = R.uuid()
        val ws = mockk<Notification>()
        every { ws.revokeUser(userId) } returns 2
        val sse = mockk<Notification>()
        every { sse.revokeUser(userId) } returns 1
        val objectMapper = mockk<ObjectMapper>()
        stubReadValue(objectMapper, "LOGOUT", userId, eventId)
        val controller = KafkaKeycloakController(objectMapper, listOf(ws, sse))

        runBlocking { controller.listen(keycloakEvent("LOGOUT", userId, eventId)) }

        verify(exactly = 1) { ws.revokeUser(userId) }
        verify(exactly = 1) { sse.revokeUser(userId) }
    }

    @Test
    fun `LOGIN and REGISTER events do not revoke sessions`() {
        val userId = R.sub("quiet-user")
        val ws = mockk<Notification>(relaxed = true)
        val sse = mockk<Notification>(relaxed = true)
        val objectMapper = mockk<ObjectMapper>()
        val loginId = R.uuid()
        stubReadValue(objectMapper, "LOGIN", userId, loginId)
        val controller = KafkaKeycloakController(objectMapper, listOf(ws, sse))

        runBlocking {
            controller.listen(keycloakEvent("LOGIN", userId, loginId))
            val registerEvent = KeycloakUserEvent(
                id = R.uuid(), time = 2, type = KeycloakEventType.REGISTER,
                realmId = "app", clientId = "frontend", userId = userId, sessionId = "s2",
                ipAddress = "127.0.0.1", error = null, details = emptyMap(),
            )
            every { objectMapper.readValue(any<String>(), KeycloakUserEvent::class.java) } returns registerEvent
            controller.listen(keycloakEvent("REGISTER", userId, registerEvent.id))
        }

        verify(exactly = 0) { ws.revokeUser(any()) }
        verify(exactly = 0) { sse.revokeUser(any()) }
    }
}
