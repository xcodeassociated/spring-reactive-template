package com.softeno.template.sample.http.external

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.softeno.template.sample.http.internal.SampleResponseDto
import com.softeno.template.testsupport.BaseIntegrationTest
import com.softeno.template.testsupport.R
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

/**
 * External-service demo of the /external endpoints: WebClient + circuit breaker
 * against a WireMock stub on :4500. Stubs and expectations use [R]-generated payloads.
 */
class ExternalControllerIT : BaseIntegrationTest(), ExternalApiAbility {

    @Autowired
    private lateinit var webclient: WebClient

    private val wiremock: WireMockServer = WireMockServer(options().port(4500))

    @BeforeEach
    fun `setup wiremock`() {
        wiremock.resetAll()
        wiremock.start()
    }

    @AfterEach
    fun `stop wiremock`() {
        wiremock.stop()
        wiremock.resetAll()
    }

    @Test
    fun `mock external service with wiremock`() = runTest {
        // given
        val data = R.data()
        mockGetId(wiremock, data)

        // expect
        val response = webclient.get().uri("http://localhost:4500/sample/${R.data()}")
            .retrieve()
            .bodyToMono<SampleResponseDto>()
            .awaitSingle()

        assertEquals(SampleResponseDto(data), response)
    }

    @Test
    fun `should proxy external controller response`() {
        // given
        val data = R.data()
        mockGetId(wiremock, data)

        // expect
        webTestClient.get().uri("/external/${R.data()}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("data").isEqualTo(data)
    }
}
