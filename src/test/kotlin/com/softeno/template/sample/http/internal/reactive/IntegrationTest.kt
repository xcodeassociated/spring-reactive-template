package com.softeno.template.sample.http.internal.reactive

import com.ninjasquad.springmockk.MockkBean
import com.softeno.template.SoftenoReactiveMongoApp
import com.softeno.template.fixture.PermissionFixture
import com.softeno.template.users.http.reactive.PermissionsReactiveRepository
import com.softeno.template.users.http.reactive.UserReactiveRepository
import io.mockk.every
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


@SpringBootTest(classes = [SoftenoReactiveMongoApp::class],
    properties = ["spring.profiles.active=integration"],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [BaseIntegrationTest.Companion.Initialaizer::class])
@EnableConfigurationProperties
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
    private lateinit var webClient: WebTestClient

    @Test
    fun shouldReturnEmptyPermissionResponse() {
        webClient.get().uri("/reactive/permissions")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("[]")
    }
}


class ReactivePermissionMockedTest : BaseIntegrationTest(), PermissionFixture {

    @MockkBean
    @Order(value = Ordered.HIGHEST_PRECEDENCE)
    lateinit var permissionsReactiveRepositoryMock: PermissionsReactiveRepository

    @Autowired
    private lateinit var webClient: WebTestClient

    @Test
    fun `should return mocked permissions`() {
        // given
        val aPermission = aPermission()
        every { permissionsReactiveRepositoryMock.findAllBy(any()) }.answers {
            Flux.just(aPermission)
        }
        every { permissionsReactiveRepositoryMock.deleteAll() }.answers { Mono.empty() }

        // expect
        webClient.get().uri("/reactive/permissions")
            .exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("name", aPermission.name)

    }


}