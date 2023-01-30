package com.softeno.template.reactive

import org.apache.commons.logging.LogFactory
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

data class SampleResponseDto(val data: String)

// Public resource
@RestController
@RequestMapping("/sample")
@Validated
class SampleController(private val sampleService: SampleService) {

    @PostMapping
    fun postHandler(@RequestBody request: SampleResponseDto): Mono<SampleResponseDto> =
        sampleService.postHandler(request)

    @GetMapping("/{id}")
    fun getHandler(@PathVariable id: String): Mono<SampleResponseDto> = sampleService.getHandler(id)

    @PutMapping("/{id}")
    fun putHandler(@PathVariable id: String, @RequestBody request: SampleResponseDto): Mono<SampleResponseDto> =
        sampleService.putHandler(id, request)
    @DeleteMapping("/{id}")
    fun deleteHandler(@PathVariable id: String): Mono<Void> = sampleService.deleteHandler(id)

}

@Service
class SampleService{
    private val log = LogFactory.getLog(javaClass)

    fun postHandler(request: SampleResponseDto): Mono<SampleResponseDto> {
        log.info("[sample-service]: POST request: $request")
        return Mono.just(request)
    }

    fun getHandler(id: String): Mono<SampleResponseDto> {
        log.info("[sample-service]: GET id: $id")
        return Mono.just(SampleResponseDto(data = id))
    }

    fun putHandler(id: String, request: SampleResponseDto): Mono<SampleResponseDto> {
        log.info("[sample-service]: PUT id: $id, request: $request")
        return Mono.just(request)
    }

    fun deleteHandler(id: String): Mono<Void> {
        log.info("[sample-service]: GET id: $id")
        return Mono.empty()
    }

}