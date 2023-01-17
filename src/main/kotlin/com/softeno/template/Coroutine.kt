package com.softeno.template

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono


@Repository
interface PermissionCoroutineRepository :
    CoroutineCrudRepository<Permission, String>, ReactiveQuerydslPredicateExecutor<Permission> {
    fun findAllBy(pageable: Pageable): Flow<Permission>
}

@RestController
@RequestMapping("/coroutine/")
@Validated
class CoroutinePermissionController(
    val permissionCoroutineRepository: PermissionCoroutineRepository,
    val permissionsReactiveMongoTemplate: PermissionsReactiveMongoTemplate,
    val permissionsReactiveRepository: PermissionsReactiveRepository
) {
    @GetMapping("/permissions")
    fun getAllPermissions(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String
    ): Flow<Permission> {
        val pageRequest = Sort.by(Sort.Order(if (direction == "ASC") Sort.Direction.ASC else Sort.Direction.DESC, sort))
            .let { PageRequest.of(page, size, it) }
        return permissionCoroutineRepository.findAllBy(pageRequest)
    }

    @GetMapping("/permissions/{id}")
    suspend fun getPermission(@PathVariable id: String): Permission? {
        // note: it can be done by: permissionCoroutineRepository.findById(id) -> Permission?
        val permission = QPermission.permission
        val predicate = permission.id.eq(id)
        return permissionsReactiveRepository.findOne(predicate).asFlow().firstOrNull()
    }

    @DeleteMapping("/permissions/{id}")
    suspend fun deletePermission(@PathVariable id: String) = permissionCoroutineRepository.deleteById(id)

    @PostMapping("/permissions")
    suspend fun createPermission(@RequestBody(required = true) input: PermissionInput): Permission =
        permissionCoroutineRepository.save(Permission(id = null, name = input.name, description = input.description))

    @PutMapping("/permissions/{id}")
    suspend fun updatePermission(@PathVariable id: String, @RequestBody(required = true) input: PermissionInput): Permission? =
        // note: we're using reactive api as flow, and then we can get the flow element
        permissionsReactiveMongoTemplate.findAndModify(id, input)
            .switchIfEmpty(Mono.error(RuntimeException("Permission: $id Not Found")))
            .asFlow()
            .firstOrNull()
}

@Repository
interface UserCoroutineRepository : CoroutineCrudRepository<User, String> {
    fun findAllBy(pageable: Pageable): Flow<User>
}

@RestController
@RequestMapping("/coroutine/")
@Validated
class CoroutineUserController(
    val userCoroutineRepository: UserCoroutineRepository,
    val permissionCoroutineRepository: PermissionCoroutineRepository
) {

    @GetMapping("/users/unmapped")
    fun getAllUsers(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String
    ): Flow<User> {
        val pageRequest = Sort.by(Sort.Order(if (direction == "ASC") Sort.Direction.ASC else Sort.Direction.DESC, sort))
            .let { PageRequest.of(page, size, it) }
        return userCoroutineRepository.findAllBy(pageRequest)
    }

    @PostMapping("/users")
    suspend fun createUser(@RequestBody(required = true) input: UserInput): User {
        val permissions: List<Permission> = input.permissionIds.asFlow()
            .map { permissionCoroutineRepository.findById(it)
                ?: throw RuntimeException("error: permission not found")
            }
            .toList()

        return userCoroutineRepository.save(
            User(id = null, name = input.name, email = input.email, permissions = permissions.map { permission -> permission.id!! }.toSet())
        )
    }

    @PutMapping("/users/{id}")
    suspend fun updateUser(@PathVariable id: String, @RequestBody(required = true) input: UserInput): User {
        val permissions: List<Permission> = input.permissionIds
            .map { permissionCoroutineRepository.findById(it) ?: throw RuntimeException("error: permission not found") }

        val user = userCoroutineRepository.findById(id) ?: throw RuntimeException("error: user not found")
        return userCoroutineRepository.save(
            user.copy(name = input.name, email = input.email, permissions = permissions.map { it.id!! }.toSet())
        )
    }

    @DeleteMapping("/users/{id}")
    suspend fun deleteUser(@PathVariable id: String) {
        if (!userCoroutineRepository.existsById(id)) {
            throw RuntimeException("error: user does not exists")
        }
        userCoroutineRepository.deleteById(id)
    }

    @GetMapping("/users/{id}")
    suspend fun getUserWithMappedPermissions(@PathVariable id: String): UserDto? {
        val user: User = userCoroutineRepository.findById(id) ?: return null
        val userPermissions = user.permissions.asFlow()
            .map { permissionCoroutineRepository.findById(it) }
            .filterNotNull()
            .toList()

        return UserDto(id = user.id!!, name = user.name, email = user.email, permissions = userPermissions)
    }

    @GetMapping("/users")
    fun getAllUsersMapped(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String
    ): Flow<UserDto> {
        val pageRequest = Sort.by(Sort.Order(if (direction == "ASC") Sort.Direction.ASC else Sort.Direction.DESC, sort))
            .let { PageRequest.of(page, size, it) }
        return userCoroutineRepository.findAllBy(pageRequest)
            .map { e ->
                UserDto(id = e.id!!, name = e.name, email = e.email, permissions = e.permissions.mapNotNull { permissionCoroutineRepository.findById(it) })
            }
    }
}