package com.softeno.template.app.user.api

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.softeno.template.app.permission.api.PermissionDto
import com.softeno.template.app.permission.api.toDto
import com.softeno.template.app.user.User
import com.softeno.template.app.user.UserModifyCommand
import com.softeno.template.app.user.UserService
import io.micrometer.tracing.Tracer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.apache.commons.logging.LogFactory
import org.slf4j.MDC
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.security.Principal
import java.time.LocalDateTime


@Component
class ExampleWebFilter : WebFilter {
    private val log = LogFactory.getLog(javaClass)
    override fun filter(serverWebExchange: ServerWebExchange, webFilterChain: WebFilterChain): Mono<Void> {
        val headers = serverWebExchange.request.headers
        val uri = serverWebExchange.request.uri
        val queryParams = serverWebExchange.request.queryParams
        log.trace("Incoming request uri: $uri")
        log.trace("Incoming request queryParams: $queryParams")
        log.trace("Incoming request headers: $headers")

        serverWebExchange.getResponse().getHeaders().add("custom-header", "XD");
        return webFilterChain.filter(serverWebExchange);
    }
}

// note: controllers methods are being triggered by netty in Dispatchers.IO coroutine context,
// so there is no need to running them with: withContext(MDCContext());
//
// If the services are using repositories or other suspended calls inside their methods,
// the entire method of service should be wrapped with: withContext(MDCContext())
// to make sure that the traceId and the context is restored;

@RestController
@RequestMapping("/coroutine/")
@Validated
class CoroutineUserController(
    val userService: UserService,
    val tracer: Tracer
) {
    private val log = LogFactory.getLog(javaClass)

    @PostMapping("/users")
    suspend fun createUser(@RequestBody(required = true) input: UserModifyCommand): UserDto =
        userService.create(input).toDto()

    @PutMapping("/users/{id}")
    suspend fun updateUser(@PathVariable id: String, @RequestBody(required = true) input: UserModifyCommand): UserDto =
        userService.update(id, input).toDto()

    @DeleteMapping("/users/{id}")
    suspend fun deleteUser(@PathVariable id: String) = userService.delete(id)

    @GetMapping("/users/{id}")
    suspend fun getUserWithMappedPermissions(@PathVariable id: String): UserDto? = userService.get(id).toDto()

    @GetMapping("/users")
    suspend fun getAllUsersMapped(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String,
        @AuthenticationPrincipal monoPrincipal: Mono<Principal>,
    ): Flow<UserDto> {
        // debug only
        val traceId = tracer.currentSpan()?.context()?.traceId()
        val mdc = MDC.get("traceId")
        val mdcSpan = MDC.get("spanId")
        log.debug("Show traceId=$traceId, mdcTraceId=$mdc and mdcSpanId=$mdcSpan")

        return userService.getAll(page, size, sort, direction, monoPrincipal).map { it.toDto() }
    }

    @GetMapping("/usersCount")
    suspend fun getUserSize(): Long = userService.size()
}

data class UserDto(
    @JsonProperty("_id")
    val id: String,
    val name: String,
    val email: String,
    @JsonProperty("role")
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val permissions: List<PermissionDto>?,
    val version: Long?,
    val createdBy: String?,
    val createdDate: LocalDateTime?,
    val modifiedBy: String?,
    val modifiedDate: LocalDateTime?
)

fun User.toDto(): UserDto = UserDto(
    id = this.id!!,
    name = this.name,
    email = this.email,
    permissions = permissions?.map { it.toDto() },
    version = this.base?.version,
    createdBy = this.base?.createdBy,
    createdDate = this.base?.createdDate,
    modifiedBy = this.base?.modifiedBy,
    modifiedDate = this.base?.modifiedDate
)