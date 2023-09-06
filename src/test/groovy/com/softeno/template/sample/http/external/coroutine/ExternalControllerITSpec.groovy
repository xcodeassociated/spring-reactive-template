package com.softeno.template.sample.http.external.coroutine

import com.github.tomakehurst.wiremock.WireMockServer
import com.softeno.template.BaseAppSpec
import com.softeno.template.sample.http.dto.SampleResponseDto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

class ExternalControllerITSpec extends BaseAppSpec implements ExternalApiAbility {

    @Autowired
    WebTestClient webClient

    WireMockServer wiremock = new WireMockServer(options().port(4500))

    def setup() {
        wiremock.start()
    }

    def cleanup() {
        wiremock.stop()
        wiremock.resetAll()
    }

    def "check if wiremock work for external service"() {
        given:
        mockGetId(wiremock)
        def expected = new SampleResponseDto("1")

        when:
        def response = webClient.get()
                .uri("http://localhost:4500/sample/100")
                .exchange()
                .returnResult(SampleResponseDto).responseBody
                .blockFirst()

        then:
        response == expected
    }

    def "should return external data"() {
        given:
        mockGetId(wiremock)

        expect:
        webClient.get().uri("/external/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("data").isEqualTo("1")

    }
}

trait ExternalApiAbility {

    def "mockGetId"(WireMockServer wiremock) {
        wiremock.stubFor(
                get(urlMatching("/sample/.*"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody("{\"data\":\"1\"}")
                        )
        )
    }
}