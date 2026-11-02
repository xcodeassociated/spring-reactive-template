package com.softeno.template.notifications.kafka

import com.softeno.template.notifications.Notification
import com.softeno.template.testsupport.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

/**
 * Unit tests for the Keycloak ADMIN-events listener: [AdminDirective.of]
 * interprets the wire shape ([KeycloakEventFixture] payloads with
 * [R]-randomized subjects), and [KafkaKeycloakAdminController] executes the
 * directive — an admin session termination (`ACTION` on `users/{id}/logout`)
 * and a user deletion (`DELETE` on `users/{id}`) revoke the user on every
 * [Notification] adapter; everything else interprets to [AdminDirective.Ignore]
 * and leaves connections alone.
 */
class KafkaKeycloakAdminControllerTest : KeycloakEventFixture {

    private fun stubReadValue(objectMapper: ObjectMapper, event: KeycloakAdminEvent) {
        every { objectMapper.readValue(any<String>(), KeycloakAdminEvent::class.java) } returns event
    }

    private fun adminEvent(operationType: String, resourceType: String, resourcePath: String?) =
        KeycloakAdminEvent(
            time = System.currentTimeMillis(), realmId = "app",
            resourceType = resourceType, operationType = operationType, resourcePath = resourcePath,
        )

    @Test
    fun `admin session-logout ACTION revokes the user on every adapter`() {
        val userId = R.sub("admin-terminated")
        val ws = mockk<Notification>()
        every { ws.revokeUser(userId) } returns 1
        val sse = mockk<Notification>()
        every { sse.revokeUser(userId) } returns 1
        val objectMapper = mockk<ObjectMapper>()
        stubReadValue(objectMapper, adminEvent("ACTION", "USER", "users/$userId/logout"))
        val controller = KafkaKeycloakAdminController(objectMapper, listOf(ws, sse))

        runBlocking { controller.listen(keycloakAdminEvent("USER", "ACTION", "users/$userId/logout")) }

        verify(exactly = 1) { ws.revokeUser(userId) }
        verify(exactly = 1) { sse.revokeUser(userId) }
    }

    @Test
    fun `user DELETE revokes the user`() {
        val userId = R.sub("deleted-user")
        val sse = mockk<Notification>()
        every { sse.revokeUser(userId) } returns 1
        val objectMapper = mockk<ObjectMapper>()
        stubReadValue(objectMapper, adminEvent("DELETE", "USER", "users/$userId"))
        val controller = KafkaKeycloakAdminController(objectMapper, listOf(sse))

        runBlocking { controller.listen(keycloakAdminEvent("USER", "DELETE", "users/$userId")) }

        verify(exactly = 1) { sse.revokeUser(userId) }
    }

    @Test
    fun `non-revoking admin events leave connections alone`() {
        val userId = R.sub("touched-user")
        val ws = mockk<Notification>(relaxed = true)
        val sse = mockk<Notification>(relaxed = true)
        val objectMapper = mockk<ObjectMapper>()
        val controller = KafkaKeycloakAdminController(objectMapper, listOf(ws, sse))

        runBlocking {
            stubReadValue(objectMapper, adminEvent("UPDATE", "USER", "users/$userId"))
            controller.listen(keycloakAdminEvent("USER", "UPDATE", "users/$userId"))
            stubReadValue(objectMapper, adminEvent("CREATE", "USER", "users/$userId"))
            controller.listen(keycloakAdminEvent("USER", "CREATE", "users/$userId"))
            stubReadValue(objectMapper, adminEvent("ACTION", "REALM", "sessions"))
            controller.listen(keycloakAdminEvent("REALM", "ACTION", "sessions"))
            stubReadValue(objectMapper, adminEvent("ACTION", "USER", "users/$userId/impersonate"))
            controller.listen(keycloakAdminEvent("USER", "ACTION", "users/$userId/impersonate"))
            stubReadValue(objectMapper, adminEvent("ACTION", "USER", null))
            controller.listen(keycloakAdminEvent("USER", "ACTION", null))
        }

        verify(exactly = 0) { ws.revokeUser(any()) }
        verify(exactly = 0) { sse.revokeUser(any()) }
    }

    @Test
    fun `revoking events interpret to RevokeUser with the addressed user`() {
        val userId = R.sub("directive")
        assertEquals(
            AdminDirective.RevokeUser(userId),
            AdminDirective.of(adminEvent("ACTION", "USER", "users/$userId/logout")),
        )
        assertEquals(
            AdminDirective.RevokeUser(userId),
            AdminDirective.of(adminEvent("DELETE", "USER", "users/$userId")),
        )
    }

    @Test
    fun `everything else interprets to Ignore`() {
        val userId = R.sub("directive")
        listOf(
            adminEvent("UPDATE", "USER", "users/$userId"),
            adminEvent("CREATE", "USER", "users/$userId"),
            adminEvent("ACTION", "REALM", "sessions"),
            adminEvent("ACTION", "USER", "users/$userId/impersonate"),
            adminEvent("ACTION", "USER", null),
            adminEvent("DELETE", "USER", "groups/$userId"),
            adminEvent("DELETE", "CLIENT", "clients/$userId"),
            adminEvent("DELETE", "USER", "users/$userId/credentials"),
        ).forEach { assertEquals(AdminDirective.Ignore, AdminDirective.of(it), "$it should be ignored") }
    }

    @Test
    fun `real admin logout payload deserializes into the revocation directive`() {
        // captured wire shape of a console "Sign out of all sessions":
        // authDetails object, null representation/error, resourceTypeAsString
        val userId = R.sub("wire-logout")
        val raw = """{"id":"${R.uuid()}","time":1793644804578,"realmId":"015343f0","realmName":"app",
            "authDetails":{"realmId":"f74c34db","realmName":"master","clientId":"5a6abd43","userId":"53d564d1"},
            "resourceType":"USER","operationType":"ACTION","resourcePath":"users/$userId/logout",
            "representation":null,"error":null,"resourceTypeAsString":"USER"}"""
        val event = tools.jackson.databind.json.JsonMapper.builder().build()
            .readValue(raw, KeycloakAdminEvent::class.java)

        assertEquals("ACTION", event.operationType)
        assertEquals(AdminDirective.RevokeUser(userId), AdminDirective.of(event))
    }
}
