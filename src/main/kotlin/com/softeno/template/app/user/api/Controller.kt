package com.softeno.template.app.user.api

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.softeno.template.app.permission.api.PermissionDto
import com.softeno.template.app.user.UserModifyCommand
import com.softeno.template.app.user.mapper.toDto
import com.softeno.template.app.user.service.UserService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.security.Principal
import java.time.LocalDateTime


@RestController
@RequestMapping("/coroutine/")
@Validated
class CoroutineUserController(
    val userService: UserService
) {

    @PostMapping("/users")
    suspend fun createUser(@RequestBody(required = true) input: UserModifyCommand): UserDto =
        userService.create(input).toDto()

    @PutMapping("/users/{id}")
    suspend fun updateUser(@PathVariable id: String, @RequestBody(required = true) input: UserModifyCommand): UserDto =
        userService.update(id, input).toDto()

    @DeleteMapping("/users/{id}")
    suspend fun deleteUser(@PathVariable id: String) = userService.delete(id)

    @GetMapping("/users/{id}")
    suspend fun getUserWithMappedPermissions(@PathVariable id: String): UserDto? {
        val user = userService.get(id)
        return user.toDto()
    }

    @GetMapping("/users")
    suspend fun getAllUsersMapped(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String,
        @AuthenticationPrincipal monoPrincipal: Mono<Principal>,
    ): Flow<UserDto> = userService.getAll(page, size, sort, direction, monoPrincipal).map { it.toDto() }

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