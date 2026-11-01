package com.softeno.template.sample.http.internal.router

import com.softeno.template.sample.http.internal.SampleResponseDto
import com.softeno.template.testsupport.BaseIntegrationTest
import com.softeno.template.testsupport.R
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

/**
 * Functional-endpoint (RouterFunctions) demo of the /sample-secured endpoints —
 * the suspend-service bridge (`mono { MDCContext() }`), exercised with random data.
 */
class SampleRouterIT : BaseIntegrationTest() {

    @Test
    fun `should return id on GET`() {
        val id = R.data()

        webTestClient.get().uri("/sample-secured/$id")
            .exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("data").isEqualTo(id)
    }

    @Test
    fun `should echo body on POST`() {
        val request = SampleResponseDto(R.data())

        webTestClient.post().uri("/sample-secured")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("data").isEqualTo(request.data)
    }

    @Test
    fun `should echo body on PUT`() {
        val request = SampleResponseDto(R.data())

        webTestClient.put().uri("/sample-secured/${R.data()}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("data").isEqualTo(request.data)
    }

    @Test
    fun `should accept DELETE`() {
        webTestClient.delete().uri("/sample-secured/${R.data()}")
            .exchange()
            .expectStatus().isOk()
    }
}
