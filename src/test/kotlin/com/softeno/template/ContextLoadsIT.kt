package com.softeno.template

import com.softeno.template.testsupport.BaseIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.config.KafkaListenerEndpointRegistry

/**
 * Boot smoke test: the full application context starts with Kafka + Mongo
 * Testcontainers running.
 */
class ContextLoadsIT : BaseIntegrationTest() {

    @Autowired
    lateinit var listenerRegistry: KafkaListenerEndpointRegistry

    @Test
    fun contextLoads() {
        assertTrue(mongoDBContainer.isRunning)
        assertTrue(kafka.isRunning)
    }

    @Test
    fun `every fan-out listener resolves a per-instance group id (ADR-0008)`() {
        // the group-id suffix chain (${INSTANCE_ID:${HOSTNAME:standalone}}) must
        // resolve for ALL notification listeners — a bare group id here means a
        // regression to competing-consumer semantics (events land on one pod only)
        val group = "sample-group-jvm" // spring.kafka.consumer.group-id in test properties
        val ids = listenerRegistry.listenerContainerIds.toList()

        assertEquals(3, ids.size, "expected the rx + keycloak + keycloak-admin listeners, got: $ids")
        ids.forEach { id ->
            assertTrue(id.startsWith("$group"), "$id must extend the base group")
            assertTrue(id.length > group.length + 1, "$id must carry the per-instance suffix")
        }
    }
}
