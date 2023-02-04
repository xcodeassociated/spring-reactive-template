package com.softeno.template.sample.http.internal.reactive

import com.fasterxml.jackson.databind.ObjectMapper
import com.softeno.template.SoftenoReactiveMongoApp
import com.softeno.template.sample.http.dto.SampleResponseDto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import spock.lang.Specification

@SpringBootTest(classes = SoftenoReactiveMongoApp,
        properties = "spring.profiles.active=integration",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableConfigurationProperties
@ConfigurationPropertiesScan("com.softeno")
class SampleEndpointsSpec extends Specification {

    @Autowired
    WebTestClient webClient

    def "GET /sample/ -> 200"() {
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

    def "POST /sample -> 200"() {
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