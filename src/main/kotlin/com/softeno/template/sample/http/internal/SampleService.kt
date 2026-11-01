package com.softeno.template.sample.http.internal

import org.apache.commons.logging.LogFactory
import org.springframework.stereotype.Service

data class SampleResponseDto(val data: String)

/**
 * In-memory sample resource backing the internal HTTP demos.
 * Coroutine-style — the functional router (`router/Router.kt`) bridges these
 * suspend calls into RouterFunctions with `mono { }`.
 */
@Service
class SampleService {
    private val log = LogFactory.getLog(javaClass)

    suspend fun postHandler(request: SampleResponseDto): SampleResponseDto {
        log.info("[sample-service]: POST request: $request")
        return request
    }

    suspend fun getHandler(id: String): SampleResponseDto {
        log.info("[sample-service]: GET id: $id")
        return SampleResponseDto(data = id)
    }

    suspend fun putHandler(id: String, request: SampleResponseDto): SampleResponseDto {
        log.info("[sample-service]: PUT id: $id, request: $request")
        return request
    }

    suspend fun deleteHandler(id: String) {
        log.info("[sample-service]: DELETE id: $id")
    }
}
