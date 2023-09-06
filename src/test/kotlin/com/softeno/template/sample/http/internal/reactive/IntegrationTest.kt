package com.softeno.template.sample.http.internal.reactive

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.ninjasquad.springmockk.MockkBean
import com.softeno.template.SoftenoReactiveMongoApp
import com.softeno.template.fixture.PermissionFixture
import com.softeno.template.sample.http.dto.SampleResponseDto
import com.softeno.template.users.http.reactive.PermissionsReactiveRepository
import com.softeno.template.users.http.reactive.UserReactiveRepository
import io.mockk.every
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.web.reactive.function.client.WebClient
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


@SpringBootTest(
    classes = [SoftenoReactiveMongoApp::class],
    properties = ["spring.profiles.active=integration"],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(initializers = [BaseIntegrationTest.Companion.Initialaizer::class])
@EnableConfigurationProperties
@ConfigurationPropertiesScan("com.softeno")
@AutoConfigureWebTestClient(timeout = "6000")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BaseIntegrationTest {

    companion object {

        private const val databaseName = "example1"

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
    lateinit var webTestClient: WebTestClient

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

    @Test
    fun shouldReturnEmptyPermissionResponse() {
        webTestClient.get().uri("/reactive/permissions")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("[]")
    }
}

class ReactivePermissionMockedTest : BaseIntegrationTest(), PermissionFixture {

    @MockkBean
    @Order(value = Ordered.HIGHEST_PRECEDENCE)
    lateinit var permissionsReactiveRepositoryMock: PermissionsReactiveRepository

    @Test
    fun `should return mocked permissions`() {
        // given
        val aPermission = aPermission()

        every { permissionsReactiveRepositoryMock.findAllBy(any()) }.answers {
            Flux.just(aPermission)
        }
        every { permissionsReactiveRepositoryMock.deleteAll() }.answers { Mono.empty() }

        // expect
        webTestClient.get().uri("/reactive/permissions")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("[0].name").isEqualTo(aPermission.name)
            .jsonPath("[0].description").isEqualTo(aPermission.description)

    }

}

@OptIn(ExperimentalCoroutinesApi::class)
class ExternalControllerTest : BaseIntegrationTest(), ExternalApiAbility {

    @Autowired
    private lateinit var webclient: WebClient

    private val wiremock: WireMockServer = WireMockServer(options().port(4500))

    @BeforeEach
    fun `setup wiremock`() {
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
        mockGetId(wiremock)

        val expected = SampleResponseDto(data = "1")

        // expect
        val response = webclient.get().uri("http://localhost:4500/sample/100")
            .retrieve()
            .bodyToMono(SampleResponseDto::class.java)
            .awaitSingle()

        assertEquals(expected, response)
    }

    @Test
    fun `test external controller`() {
        // given
        mockGetId(wiremock)

        // expect
        webTestClient.get().uri("/external/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("data").isEqualTo("1")
    }
}

interface ExternalApiAbility {

    fun mockGetId(wiremock: WireMockServer) {
        wiremock.stubFor(
            get(urlMatching("/sample/.*"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                    {
                        "data": "1"
                    }
                """.trimIndent()
                        )
                )
        )
    }
}