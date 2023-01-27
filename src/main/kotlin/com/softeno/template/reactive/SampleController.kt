package com.softeno.template.reactive

import org.apache.commons.logging.LogFactory
import org.springframework.stereotype.Controller
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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