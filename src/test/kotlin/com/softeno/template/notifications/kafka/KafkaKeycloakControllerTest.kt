package com.softeno.template.notifications.kafka

import com.softeno.template.notifications.Notification
import com.softeno.template.testsupport.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `real keycloak logout payload with null details deserializes`() {
        // the account-client LOGOUT payload carries "details": null and an extra
        // realmName field — the listener used to retry-burn on it (NPE in the ctor)
        val raw = """{"id":"230e3401-7959-45ab-9697-d875b78bd5c1","time":1793569620548,"type":"LOGOUT",
            "realmId":"015343f0-98e7-4ab9-8000-99c1836a7ec0","realmName":"app","clientId":"account",
            "userId":"05dbf0b6-057b-44b4-aeaa-d83d662c5833","sessionId":"86d88131-4a24-4a8f-ae3c-9d2b6768084f",
            "ipAddress":"192.168.65.1","error":null,"details":null}"""
        val event = tools.jackson.databind.json.JsonMapper.builder().build()
            .readValue(raw, KeycloakUserEvent::class.java)

        assertEquals(KeycloakEventType.LOGOUT, event.type)
        assertEquals("05dbf0b6-057b-44b4-aeaa-d83d662c5833", event.userId)
        assertNull(event.details)
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
