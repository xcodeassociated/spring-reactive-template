package com.softeno.template.sample.http.internal.reactive

import com.softeno.template.playground.helloCoroutine
import com.softeno.template.playground.someError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@OptIn(ExperimentalCoroutinesApi::class)
class PlaygroundTest {

    @Test
    fun testHelloCoroutine() = runTest {
        val result = helloCoroutine()
        assertEquals(result, "hello")
    }

    @Test
    fun testSomeError() = runTest {
        val exception = assertThrows<RuntimeException> { someError() }
        assertEquals(exception.message, "Some Error Occurred")
    }

}