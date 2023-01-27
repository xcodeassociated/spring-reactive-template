package com.softeno.template.reactive

import org.apache.commons.logging.LogFactory
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

data class SampleResponseDto(val data: String)

@RestController
@RequestMapping("/sample")
@Validated
class SampleController {
    private val log = LogFactory.getLog(javaClass)

    @PostMapping
    fun postHandler(@RequestBody request: SampleResponseDto): Mono<SampleResponseDto> {
        log.info("[sample]: POST request: $request")
        return Mono.just(request)
    }

    @GetMapping("/{id}")
    fun getHandler(@PathVariable id: String): Mono<SampleResponseDto> {
        log.info("[sample]: GET id: $id")
        return Mono.just(SampleResponseDto(data = id))
    }

    @PutMapping("/{id}")
    fun putHandler(@PathVariable id: String, @RequestBody request: SampleResponseDto): Mono<SampleResponseDto> {
        log.info("[sample]: PUT id: $id, request: $request")
        return Mono.just(request)
    }

    @DeleteMapping("/{id}")
    fun deleteHandler(@PathVariable id: String): Mono<Void> {
        log.info("[sample]: GET id: $id")
        return Mono.empty()
    }

}