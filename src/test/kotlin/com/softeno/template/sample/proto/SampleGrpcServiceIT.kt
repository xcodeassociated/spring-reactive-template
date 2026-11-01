package com.softeno.template.sample.proto

import com.softeno.template.grpc.SampleGrpcServiceGrpcKt
import com.softeno.template.grpc.SampleRequest
import com.softeno.template.testsupport.BaseIntegrationTest
import com.softeno.template.testsupport.R
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcPort

/**
 * gRPC service demo (`sample/proto`): unary, server-stream, client-stream and
 * bidirectional echo — asserted against [R]-generated payloads.
 */
class SampleGrpcServiceIT : BaseIntegrationTest() {

    @LocalGrpcPort
    private var port: Int = 0

    private lateinit var testStub: SampleGrpcServiceGrpcKt.SampleGrpcServiceCoroutineStub

    @BeforeEach
    fun setup() {
        val channel = ManagedChannelBuilder
            .forAddress("localhost", port)
            .usePlaintext()
            .build()

        testStub = SampleGrpcServiceGrpcKt.SampleGrpcServiceCoroutineStub(channel)
    }

    private fun request(data: String) =
        SampleRequest.newBuilder().setData(data).build()

    @Test
    fun `should echo unary`() = runBlocking {
        val data = R.data()

        val response = testStub.echo(request(data))

        assertEquals("Echo: $data", response.data)
    }

    @Test
    fun `should stream responses`() = runBlocking {
        val data = R.data()

        val responses = testStub.echoServerStream(request(data)).toList()

        assertEquals(5, responses.size)
        assertEquals("Stream[0]: $data", responses.first().data)
        assertEquals("Stream[4]: $data", responses.last().data)
    }

    @Test
    fun `should collect client stream`() = runBlocking {
        val words = List(3) { R.data() }

        val response = testStub.echoClientStream(words.map { request(it) }.asFlow())

        assertEquals("Collected: ${words.joinToString(", ")}", response.data)
    }

    @Test
    fun `should handle bidirectional streaming`() = runBlocking {
        val words = List(3) { R.data() }

        val responses = testStub.echoBidirectional(words.map { request(it) }.asFlow()).toList()

        assertEquals(3, responses.size)
        responses.forEachIndexed { i, response ->
            assertEquals("Reply: ${words[i]}", response.data)
        }
    }
}
