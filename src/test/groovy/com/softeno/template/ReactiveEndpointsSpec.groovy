package com.softeno.template

import com.softeno.template.reactive.PermissionsReactiveRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.reactive.server.WebTestClient
import spock.lang.Specification

@SpringBootTest(classes = SoftenoReactiveMongoApp,
        properties = "application.environment=integration",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableConfigurationProperties
@ConfigurationPropertiesScan("com.softeno")
class ReactiveEndpointsSpec extends Specification {

    @Autowired
    WebTestClient webClient

    @Autowired
    PermissionsReactiveRepository permissionsReactiveRepository

    // before spec class
    def setupSpec() {

    }

    // after spec class
    def cleanupSpec() {

    }

    // before each test
    def setup() {

    }

    // after each test
    def cleanup() {
        permissionsReactiveRepository.deleteAll()
    }

    def "web client test of /permissions with empty database"() {
        expect:
        webClient.get().uri("/reactive/permissions")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]")
    }


}