package com.softeno.template.sample.http.internal.reactive

import com.softeno.template.playground.CoroutinePlayground
import com.softeno.template.playground.helloCoroutine
import com.softeno.template.playground.someError
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
class PlaygroundTest {

    @Test
    fun testHelloCoroutine() = runTest {
        val result = helloCoroutine()
        assertEquals("hello", result)
    }

    @Test
    fun testSomeError() = runTest {
        val exception = assertThrows<RuntimeException> { someError() }
        assertEquals(exception.message, "Some Error Occurred")
    }

}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaygroundMockTest {

    @Test
    fun `test hello coroutine Mock`() = runTest {
        // setup
        mockkStatic("com.softeno.template.playground.CoroutinePlaygroundKt")

        // given
        val mockedValue = "mockk"
        coEvery { helloCoroutine() }.answers { mockedValue }

        // when
        val result = helloCoroutine()

        // then
        assertEquals(result, mockedValue)

        // clean up
        unmockkStatic("com.softeno.template.playground.CoroutinePlaygroundKt")
    }

    @ParameterizedTest(name = "{index} => input=''{0}''")
    @ValueSource(strings = ["Hello", "World"])
    fun `test coroutine playground run mocked`(input: String) = runTest {
        // given
        val mockedValue = "mockk"
        val coroutinePlayground = mockk<CoroutinePlayground>()
        coEvery { coroutinePlayground.sampleFunction(any()) }.answers { mockedValue }

        // when
        val result = coroutinePlayground.sampleFunction(input)

        // then
        assertEquals(result, mockedValue)
    }


    @ParameterizedTest
    @CsvSource(value = ["test:test", "tEst:test", "Java:java"], delimiter = ':')
    fun `test coroutine playground run mocked with multiple values`(a: String, b: String) = runTest {
        // given
        val mockedValue = "$a, $b"
        val coroutinePlayground = mockk<CoroutinePlayground>()
        coEvery { coroutinePlayground.sampleFunction(any()) }.answers { mockedValue }

        // when
        val result = coroutinePlayground.sampleFunction(a+b)

        // then
        assertEquals(result, mockedValue)
    }

}