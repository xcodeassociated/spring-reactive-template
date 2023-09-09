package com.softeno.template

import com.softeno.template.app.permission.db.PermissionsReactiveRepository
import com.softeno.template.app.user.db.UserReactiveRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Shared
import spock.lang.Specification

@Testcontainers
@SpringBootTest(classes = SoftenoReactiveMongoApp,
        properties = "spring.profiles.active=integration",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableConfigurationProperties
@EmbeddedKafka(partitions = 1, brokerProperties = ["listeners=PLAINTEXT://localhost:9092", "port=9092"])
@ConfigurationPropertiesScan("com.softeno")
class BaseAppSpec extends Specification {

    @Autowired
    PermissionsReactiveRepository permissionsReactiveRepository

    @Autowired
    UserReactiveRepository userReactiveRepository

    @Shared
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:6.0.4")

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        mongoDBContainer.withReuse(false)
        mongoDBContainer.start()
        registry.add("spring.data.mongodb.uri", () -> mongoDBContainer.replicaSetUrl)
    }

    // before each test
    def setup() {

    }

    // after each test
    void cleanup() {
        permissionsReactiveRepository.deleteAll()
        userReactiveRepository.deleteAll()
    }

    // before spec class
    def setupSpec() {

    }

    // after spec class
    def cleanupSpec() {

    }
}
