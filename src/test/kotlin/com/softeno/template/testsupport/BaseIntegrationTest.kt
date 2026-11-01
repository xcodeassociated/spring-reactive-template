package com.softeno.template.testsupport

import com.softeno.template.SoftenoReactiveMongoApp
import com.softeno.template.app.permission.db.PermissionsReactiveRepository
import com.softeno.template.app.user.db.UserReactiveRepository
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.graphql.test.autoconfigure.AutoConfigureGraphQl
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName

/**
 * Shared base for every integration test (the `*IT` files): boots the whole app
 * under the `integration` profile (permit-all test SecurityConfig) on a random
 * port with Testcontainers Kafka + Mongo, and wipes users/permissions between
 * tests. The context is rebuilt per class (@DirtiesContext) so no state leaks.
 */
@SpringBootTest(
    classes = [SoftenoReactiveMongoApp::class],
    properties = ["spring.profiles.active=integration", "spring.grpc.server.port=0"],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EnableConfigurationProperties
@EnableMongoRepositories
@ConfigurationPropertiesScan("com.softeno")
@AutoConfigureWebTestClient(timeout = "6000")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureJson
@AutoConfigureGraphQl
@AutoConfigureGraphQlTester
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
abstract class BaseIntegrationTest {

    companion object {
        @Container
        var kafka: KafkaContainer = KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"))
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withEnv("ALLOW_PLAINTEXT_LISTENER", "true")
            .withEnv("KAFKA_CREATE_TOPICS", "sample_topic_2:1:1")

        @Container
        var mongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:6.0.4"))
            .withEnv("MONGO_INITDB_DATABASE", "example1")

        @JvmStatic
        @DynamicPropertySource
        fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
            kafka.start()
            registry.add("spring.kafka.bootstrap-servers") {
                kafka.bootstrapServers
            }

            mongoDBContainer.start()
            registry.add("spring.mongodb.uri") {
                mongoDBContainer.replicaSetUrl
            }
        }
    }

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var userReactiveRepository: UserReactiveRepository

    @Autowired
    lateinit var permissionsReactiveRepository: PermissionsReactiveRepository

    @AfterEach
    fun cleanup() {
        runBlocking {
            userReactiveRepository.deleteAll().awaitFirstOrNull()
            permissionsReactiveRepository.deleteAll().awaitFirstOrNull()
        }
    }
}
