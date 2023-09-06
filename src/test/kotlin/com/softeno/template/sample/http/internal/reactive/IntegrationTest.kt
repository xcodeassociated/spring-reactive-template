package com.softeno.template.sample.http.internal.reactive

import com.softeno.template.SoftenoReactiveMongoApp
import com.softeno.template.users.http.reactive.PermissionsReactiveRepository
import com.softeno.template.users.http.reactive.UserReactiveRepository
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.fromApplication
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName



@SpringBootTest(classes = [SoftenoReactiveMongoApp::class],
    properties = ["spring.profiles.active=integration"],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [BaseIntegrationTest.Companion.Initialaizer::class])
@EnableConfigurationProperties
@EmbeddedKafka(partitions = 1, brokerProperties = ["listeners=PLAINTEXT://localhost:9093", "port=9093"])
@ConfigurationPropertiesScan("com.softeno")
@AutoConfigureWebTestClient(timeout = "6000")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseIntegrationTest {

    companion object {

        const val databaseName = "example1"

        @JvmField
        val dbContainer: MongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:6.0.4"))

        class Initialaizer : ApplicationContextInitializer<ConfigurableApplicationContext> {
            override fun initialize(applicationContext: ConfigurableApplicationContext) {
                Companion.dbContainer.start()

                TestPropertyValues.of(
                    "spring.data.mongodb.uri=${Companion.dbContainer.connectionString}/${databaseName}",
                    "mongo.database=$databaseName"
                ).applyTo(applicationContext.environment)
            }

        }
    }

    @Autowired
    lateinit var userReactiveRepository: UserReactiveRepository

    @Autowired
    lateinit var permissionsReactiveRepository: PermissionsReactiveRepository

    @BeforeEach
    fun init() {

    }

    @AfterEach
    fun cleanup() {
        runBlocking {
            userReactiveRepository.deleteAll().awaitFirstOrNull()
            permissionsReactiveRepository.deleteAll().awaitFirstOrNull()
        }
    }
}

class ContextLoadsTest : BaseIntegrationTest() {

     val dbContainer: MongoDBContainer = BaseIntegrationTest.dbContainer

    @Test
    fun contextLoads() {
        assertTrue(dbContainer.isRunning)
    }
}

class ReactivePermissionTest : BaseIntegrationTest() {

    @Autowired
    lateinit var webClient: WebTestClient

    @Test
    fun shouldReturnEmptyPermissionResponse() {
        webClient.get().uri("/reactive/permissions")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("[]")
    }
}