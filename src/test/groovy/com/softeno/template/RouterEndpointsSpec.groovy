package com.softeno.template

import com.fasterxml.jackson.databind.ObjectMapper
import com.softeno.template.router.MessageDto
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient
import spock.lang.Specification

@SpringBootTest(classes = SoftenoReactiveMongoApp,
        properties = "application.environment=integration",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RouterEndpointsSpec extends Specification {

    @Autowired
    WebTestClient webClient

    def "web client test of /"() {
        expect:
        webClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("message").isEqualTo("hello world")
    }

    def "web client test of / using expected dto"() {
        given:
        def mapper = new ObjectMapper()
        def expectedString = mapper.writeValueAsString(new MessageDto("hello world"))

        expect:
        webClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json(expectedString)
    }
}