package com.softeno.template.notifications.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.config.kafka.KafkaApplicationProperties
import com.softeno.template.notifications.Message
import com.softeno.template.notifications.Notification
import com.softeno.template.testsupport.R
import com.softeno.template.users.UserAction
import com.softeno.template.users.UserEvent
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

/**
 * Unit tests for the user-events listener: the wire-to-broadcast rendering
 * table (ADR-0006/ADR-0005 completion event) and the fan-out to every
 * [Notification] port. Real mappers — the rendering logic is what's under
 * test, not the deserializer.
 */
class UserEventsListenerTest {

    private val props = KafkaApplicationProperties("tx-topic", "rx-topic", "kc-topic")
    private val wireJson = JsonMapper.builder().build()

    private fun record(event: UserEvent): ConsumerRecord<String, JsonNode> {
        val json = wireJson.writeValueAsString(event)
        // ConsumerRecord's value type is the com.fasterxml JsonNode the Kafka
        // deserializer produces; toString() is all the listener consumes.
        val node = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(json) as JsonNode
        return ConsumerRecord("rx-topic", 0, 0, null, node)
    }

    private fun listener(vararg ports: Notification) = UserEventsListener(props, ports.toList(), wireJson)

    @Test
    fun `lifecycle actions render USER_ACTION userId and hit every port`() {
        for (action in listOf(UserAction.CREATED, UserAction.UPDATED, UserAction.DELETED)) {
            val userId = R.uuid()
            val portA = mockk<Notification>(relaxed = true)
            val portB = mockk<Notification>(relaxed = true)

            listener(portA, portB).listen(record(UserEvent(userId, action)))

            val expected = Message(from = "SYSTEM", to = "ALL", content = "USER_${action.name}: $userId")
            verify(exactly = 1) { portA.broadcast(expected) }
            verify(exactly = 1) { portB.broadcast(expected) }
        }
    }

    @Test
    fun `SYNCED renders the single USERS_SYNCED permissionId completion line`() {
        val permissionId = R.uuid()
        val port = mockk<Notification>(relaxed = true)

        // the fan-out event carries NO userId — it names the permission instead
        listener(port).listen(record(UserEvent(userId = null, action = UserAction.SYNCED, permissionId = permissionId)))

        verify(exactly = 1) {
            port.broadcast(Message("SYSTEM", "ALL", "USERS_SYNCED: $permissionId"))
        }
    }

    @Test
    fun `pre-ADR-0006 payload without action reads as CREATED`() {
        val userId = R.uuid()
        val port = mockk<Notification>(relaxed = true)
        val legacyNode =
            com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree("""{"userId":"$userId"}""") as JsonNode

        listener(port).listen(ConsumerRecord("rx-topic", 0, 0, null, legacyNode))

        verify(exactly = 1) {
            port.broadcast(Message("SYSTEM", "ALL", "USER_CREATED: $userId"))
        }
    }

    @Test
    fun `wire type keeps the userId null default only for SYNCED-shaped events`() {
        // guards the data-class contract the rendering branch relies on
        val synced = UserEvent(action = UserAction.SYNCED, permissionId = "p1")
        assertEquals(null, synced.userId)
        assertEquals("p1", synced.permissionId)
    }
}
