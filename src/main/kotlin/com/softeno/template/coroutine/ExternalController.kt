package com.softeno.template.coroutine

import com.softeno.template.reactive.SampleResponseDto
import kotlinx.coroutines.reactor.awaitSingle
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono


@RestController
@RequestMapping("/external")
@Validated
class ExternalController(
    @Qualifier(value = "external") private val webClient: WebClient
) {
    private val log = LogFactory.getLog(javaClass)

    @PostMapping
    suspend fun postHandler(@RequestBody request: SampleResponseDto): SampleResponseDto {
        log.info("[external]: POST request: $request")
        val response: SampleResponseDto = webClient.post()
            .uri("/sample")
            .body(Mono.just(request), SampleResponseDto::class.java)
            .retrieve()
            .bodyToMono(SampleResponseDto::class.java)
            .awaitSingle()

        log.info("[external]: received: $response")
        return response
    }

    @GetMapping("/{id}")
    suspend fun getHandler(@PathVariable id: String): SampleResponseDto {
        log.info("[external]: GET id: $id")
        val response: SampleResponseDto = webClient.get()
            .uri("/sample/${id}")
            .retrieve()
            .bodyToMono(SampleResponseDto::class.java)
            .awaitSingle()

        log.info("[external]: received: $response")
        return response
    }

    @PutMapping("/{id}")
    suspend fun putHandler(@PathVariable id: String, @RequestBody request: SampleResponseDto): SampleResponseDto {
        log.info("[external]: PUT id: $id, request: $request")
        val response: SampleResponseDto = webClient.put()
            .uri("/sample/${id}")
            .body(Mono.just(request), SampleResponseDto::class.java)
            .retrieve()
            .bodyToMono(SampleResponseDto::class.java)
            .awaitSingle()

        log.info("[external]: received: $response")
        return response
    }

    @DeleteMapping("/{id}")
    fun deleteHandler(@PathVariable id: String): Mono<Void> {
        log.info("[external]: GET id: $id")
        val response: Mono<Void> = webClient.delete()
            .uri("/sample/${id}")
            .retrieve()
            .bodyToMono(Void::class.java)

        log.info("[external]: received: $response")
        return response
    }

}