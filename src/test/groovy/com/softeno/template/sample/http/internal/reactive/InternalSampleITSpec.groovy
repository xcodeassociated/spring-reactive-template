package com.softeno.template.sample.http.internal.reactive

import com.fasterxml.jackson.databind.ObjectMapper
import com.softeno.template.BaseAppSpec
import com.softeno.template.sample.http.dto.SampleResponseDto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

class InternalSampleITSpec extends BaseAppSpec {

    @Autowired
    WebTestClient webClient

    def "HTTP 200 GET /sample"() {
        given:
        def data = "hello"

        expect:
        webClient.get()
                .uri("/sample/" + data)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody().jsonPath("data").isEqualTo(data)
    }

    def "HTTP 200 POST /sample"() {
        given:
        def mapper = new ObjectMapper()
        def expected = new SampleResponseDto("test")
        def expectedString = mapper.writeValueAsString(expected)

        expect:
        webClient.post()
                .uri("/sample")
                .body(Mono.just(expected), SampleResponseDto.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json(expectedString)
    }
}