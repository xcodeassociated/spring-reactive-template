package com.softeno.template.users.http.coroutine

import com.softeno.template.users.http.dto.PermissionDto
import com.softeno.template.users.http.dto.PermissionInput
import com.softeno.template.users.http.dto.UserDto
import com.softeno.template.users.http.dto.UserInput
import kotlinx.coroutines.flow.Flow
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.security.Principal


@RestController
@RequestMapping("/coroutine/")
@Validated
class CoroutinePermissionController(
    val permissionService: CoroutinePermissionService
) {
//    @PreAuthorize(value = "hasRole('admin')")
    @GetMapping("/permissions")
    fun getAllPermissions(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String,
        @AuthenticationPrincipal oauth2User: Mono<OAuth2User>
    ): Flow<PermissionDto> = permissionService.getAll(page, size, sort, direction)

    @GetMapping("/permissions/{id}")
    suspend fun getPermission(@PathVariable id: String): PermissionDto? = permissionService.get(id)

    @DeleteMapping("/permissions/{id}")
    suspend fun deletePermission(@PathVariable id: String) = permissionService.delete(id)

    @PostMapping("/permissions")
    suspend fun createPermission(@RequestBody(required = true) input: PermissionInput): PermissionDto =
        permissionService.create(input)

    @PutMapping("/permissions/{id}")
    suspend fun updatePermission(@PathVariable id: String, @RequestBody(required = true) input: PermissionInput): PermissionDto? =
        permissionService.update(id, input)
}

@RestController
@RequestMapping("/coroutine/")
@Validated
class CoroutineUserController(
    val userService: CoroutineUserService
) {

    @PostMapping("/users")
    suspend fun createUser(@RequestBody(required = true) input: UserInput): UserDto =
        userService.create(input)

    @PutMapping("/users/{id}")
    suspend fun updateUser(@PathVariable id: String, @RequestBody(required = true) input: UserInput): UserDto =
        userService.update(id, input)

    @DeleteMapping("/users/{id}")
    suspend fun deleteUser(@PathVariable id: String) = userService.delete(id)

    @GetMapping("/users/{id}")
    suspend fun getUserWithMappedPermissions(@PathVariable id: String): UserDto? = userService.get(id)

    @GetMapping("/users")
    suspend fun getAllUsersMapped(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String,
        @AuthenticationPrincipal monoPrincipal: Mono<Principal>,
    ): Flow<UserDto> = userService.getAll(page, size, sort, direction, monoPrincipal)

    @GetMapping("/usersCount")
    suspend fun getUserSize(): Long = userService.size()

}