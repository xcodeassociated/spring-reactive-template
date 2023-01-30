package com.softeno.template.sample.http.external.coroutine

import com.softeno.template.sample.http.dto.SampleResponseDto
import com.softeno.template.sample.http.external.config.ExternalClientConfig
import kotlinx.coroutines.reactor.awaitSingle
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

class ExternalServiceException(message: String) : RuntimeException(message)

@RestController
@RequestMapping("/external")
@Validated
class ExternalController(
    @Qualifier(value = "external") private val webClient: WebClient,
    private val reactiveCircuitBreakerFactory: ReactiveCircuitBreakerFactory<*, *>,
    private val config: ExternalClientConfig
) {
    private val log = LogFactory.getLog(javaClass)

    @PostMapping
    suspend fun postHandler(@RequestBody request: SampleResponseDto): SampleResponseDto {
        log.info("[external]: POST request: $request")
        val response: SampleResponseDto = webClient.post()
            .body(Mono.just(request), SampleResponseDto::class.java)
            .retrieve()
            .bodyToMono(SampleResponseDto::class.java)
            .timeout(Duration.ofMillis(1_000))
            .transform {
                val rcb = reactiveCircuitBreakerFactory.create(config.name)
                // note: custom exception might be thrown since exception handler is defined
                rcb.run(it) { Mono.just(SampleResponseDto(data = "FALLBACK")) }
            }
            .awaitSingle()

        log.info("[external]: received: $response")
        return response
    }

    @GetMapping("/{id}")
    suspend fun getHandler(@PathVariable id: String): SampleResponseDto {
        log.info("[external]: GET id: $id")
        val response: SampleResponseDto = webClient.get()
            .uri("/${id}")
            .retrieve()
            .bodyToMono(SampleResponseDto::class.java)
            .timeout(Duration.ofMillis(1_000))
            .transform {
                val rcb = reactiveCircuitBreakerFactory.create(config.name)
                // note: custom exception might be thrown since exception handler is defined
                rcb.run(it) { Mono.just(SampleResponseDto(data = "FALLBACK")) }
            }
            .awaitSingle()

        log.info("[external]: received: $response")
        return response
    }

    @PutMapping("/{id}")
    suspend fun putHandler(@PathVariable id: String, @RequestBody request: SampleResponseDto): SampleResponseDto {
        log.info("[external]: PUT id: $id, request: $request")
        val response: SampleResponseDto = webClient.put()
            .uri("/${id}")
            .body(Mono.just(request), SampleResponseDto::class.java)
            .retrieve()
            .bodyToMono(SampleResponseDto::class.java)
            .timeout(Duration.ofMillis(1_000))
            .transform {
                val rcb = reactiveCircuitBreakerFactory.create(config.name)
                // note: custom exception might be thrown since exception handler is defined
                rcb.run(it) { Mono.just(SampleResponseDto(data = "FALLBACK")) }
            }
            .awaitSingle()

        log.info("[external]: received: $response")
        return response
    }

    @DeleteMapping("/{id}")
    fun deleteHandler(@PathVariable id: String): Mono<Void> {
        log.info("[external]: GET id: $id")
        val response: Mono<Void> = webClient.delete()
            .uri("/${id}")
            .retrieve()
            .bodyToMono(Void::class.java)
            .timeout(Duration.ofMillis(1_000))
            .transform {
                val rcb = reactiveCircuitBreakerFactory.create(config.name)
                // note: custom exception might be thrown since exception handler is defined
                rcb.run(it) { Mono.empty() }
            }

        log.info("[external]: received: $response")
        return response
    }

    @GetMapping("/throw-on-fallback/{id}")
    suspend fun getHandlerNoFallback(@PathVariable id: String): SampleResponseDto {
        log.info("[external]: GET id: $id")
        val response: SampleResponseDto = webClient.get()
            .uri("/${id}")
            .retrieve()
            .bodyToMono(SampleResponseDto::class.java)
            .timeout(Duration.ofMillis(1_000))
            .transform {
                val rcb = reactiveCircuitBreakerFactory.create(config.name)
                // note: custom exception might be thrown since exception handler is defined
                rcb.run(it) { Mono.error(ExternalServiceException("EXTERNAL_SERVICE_EXCEPTION")) }
            }
            .awaitSingle()

        log.info("[external]: received: $response")
        return response
    }

}