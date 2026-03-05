package com.softeno.template.sample.http.internal.reactive

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.ninjasquad.springmockk.MockkBean
import com.softeno.template.SoftenoReactiveMongoApp
import com.softeno.template.app.permission.db.PermissionsReactiveRepository
import com.softeno.template.app.user.db.UserReactiveRepository
import com.softeno.template.fixture.PermissionFixture
import com.softeno.template.grpc.SampleGrpcServiceGrpcKt
import com.softeno.template.grpc.SampleRequest
import io.grpc.ManagedChannelBuilder
import io.mockk.every
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
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
import org.springframework.boot.graphql.test.autoconfigure.AutoConfigureGraphQl
import org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcPort
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories
import org.springframework.graphql.test.tester.GraphQlTester
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


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
            .withEnv("KAFKA_CREATE_TOPICS", "sample_topic_2" + ":1:1")

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

    @Test
    fun contextLoads() {
        assertTrue(mongoDBContainer.isRunning)
        assertTrue(kafka.isRunning)
    }
}

class ReactivePermissionTestDocument : BaseIntegrationTest() {

    @Test
    fun shouldReturnEmptyPermissionResponse() {
        webTestClient.get().uri("/reactive/permissions")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("[]")
    }
}

class ReactivePermissionMockedTestDocument : BaseIntegrationTest(), PermissionFixture {

    @MockkBean
    @Order(value = Ordered.HIGHEST_PRECEDENCE)
    lateinit var permissionsReactiveRepositoryMock: PermissionsReactiveRepository

    @BeforeEach
    fun initMockkRepository() {
        every { permissionsReactiveRepositoryMock.deleteAll() }.answers { Mono.empty() }
    }

    @Test
    fun `should return mocked permissions`() {
        // given
        val aPermission = aPermission()

        every { permissionsReactiveRepositoryMock.findAllBy(any()) }.answers {
            Flux.just(aPermission)
        }

        // expect
        webTestClient.get().uri("/reactive/permissions")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("[0].name").isEqualTo(aPermission.name)
            .jsonPath("[0].description").isEqualTo(aPermission.description)

    }

}

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
            .bodyToMono<SampleResponseDto>()
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


class GraphqlPermissionControllerTestDocument : BaseIntegrationTest(), PermissionFixture {

    @Autowired
    private lateinit var graphQlTester: GraphQlTester

    @Test
    fun `should get permissions`() = runTest {
        // given
        val aPermission = aPermissionToSave()
        permissionsReactiveRepository.save(aPermission).awaitSingle()

        val query = """
            query {
              getAllPermissions(page: 0, size:10, sort:"id", direction:"ASC") {
                name,
                description
              }
            }
        """.trimIndent()

        // note: a returned result differs from graphigl because we use: .path("getAllPermissions")
        val expected = """
            [{"name":"${aPermission.name}","description":"${aPermission.description}"}]
        """.trimIndent()

        // expect
        graphQlTester.document(query)
            .execute()
            .path("getAllPermissions")
            .matchesJson(expected)
    }
}

class SampleGrpcServiceIntegrationTest : BaseIntegrationTest() {

    @LocalGrpcPort
    private var port: Int = 0

    private lateinit var testStub: SampleGrpcServiceGrpcKt.SampleGrpcServiceCoroutineStub

    @BeforeEach
    fun setup() {
        val channel = ManagedChannelBuilder
            .forAddress("localhost", port)
            .usePlaintext()
            .build()

        testStub = SampleGrpcServiceGrpcKt.SampleGrpcServiceCoroutineStub(channel)
    }

    @Test
    fun `should echo unary`() = runBlocking {
        val response = testStub.echo(
            SampleRequest.newBuilder()
                .setData("hello")
                .build()
        )

        assertEquals("Echo: hello", response.data)
    }

    @Test
    fun `should stream responses`() = runBlocking {
        val responses = testStub
            .echoServerStream(
                SampleRequest.newBuilder()
                    .setData("stream")
                    .build()
            )
            .toList()

        assertEquals(5, responses.size)
        assertEquals("Stream[0]: stream", responses.first().data)
        assertEquals("Stream[4]: stream", responses.last().data)
    }

    @Test
    fun `should collect client stream`() = runBlocking {
        val requestFlow = flowOf(
            SampleRequest.newBuilder().setData("a").build(),
            SampleRequest.newBuilder().setData("b").build(),
            SampleRequest.newBuilder().setData("c").build()
        )

        val response = testStub.echoClientStream(requestFlow)

        assertEquals("Collected: a, b, c", response.data)
    }

    @Test
    fun `should handle bidirectional streaming`() = runBlocking {

        val requestFlow = flow {
            emit(SampleRequest.newBuilder().setData("1").build())
            emit(SampleRequest.newBuilder().setData("2").build())
            emit(SampleRequest.newBuilder().setData("3").build())
        }

        val responses = testStub.echoBidirectional(requestFlow).toList()

        assertEquals(3, responses.size)
        assertEquals("Reply: 1", responses[0].data)
        assertEquals("Reply: 3", responses[2].data)
    }
}
