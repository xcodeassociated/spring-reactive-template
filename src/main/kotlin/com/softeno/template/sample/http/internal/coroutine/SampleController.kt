package com.softeno.template.sample.http.internal.coroutine

import com.softeno.template.sample.http.internal.SampleResponseDto
import com.softeno.template.sample.http.internal.SampleService
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*


// Public resource — coroutine-style annotated controller demo
@RestController
@RequestMapping("/sample")
@Validated
class SampleController(private val sampleService: SampleService) {

    @PostMapping
    suspend fun postHandler(@RequestBody request: SampleResponseDto): SampleResponseDto =
        sampleService.postHandler(request)

    @GetMapping("/{id}")
    suspend fun getHandler(@PathVariable id: String): SampleResponseDto = sampleService.getHandler(id)

    @PutMapping("/{id}")
    suspend fun putHandler(@PathVariable id: String, @RequestBody request: SampleResponseDto): SampleResponseDto =
        sampleService.putHandler(id, request)

    @DeleteMapping("/{id}")
    suspend fun deleteHandler(@PathVariable id: String) = sampleService.deleteHandler(id)
}
