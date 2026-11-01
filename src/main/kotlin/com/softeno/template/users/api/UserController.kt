package com.softeno.template.users.api

import com.softeno.template.users.UserModifyCommand
import com.softeno.template.users.UserService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/coroutine/")
@Validated
class UserController(
    private val userService: UserService
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
    suspend fun getUserWithMappedPermissions(@PathVariable id: String): UserDto =
        userService.get(id).toDto()

    @GetMapping("/users")
    suspend fun getAllUsersMapped(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String,
    ): Flow<UserDto> = userService.getAll(page, size, sort, direction).map { it.toDto() }
    @GetMapping("/usersCount")
    suspend fun getUserSize(): Long = userService.size()
}
