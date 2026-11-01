package com.softeno.template

import com.softeno.template.testsupport.BaseIntegrationTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Boot smoke test: the full application context starts with Kafka + Mongo
 * Testcontainers running.
 */
class ContextLoadsIT : BaseIntegrationTest() {

    @Test
    fun contextLoads() {
        assertTrue(mongoDBContainer.isRunning)
        assertTrue(kafka.isRunning)
    }
}
