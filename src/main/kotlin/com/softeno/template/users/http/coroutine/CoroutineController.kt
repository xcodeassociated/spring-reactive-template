package com.softeno.template.users.http.coroutine

import com.softeno.template.*
import com.softeno.template.users.db.Permission
import com.softeno.template.users.db.QPermission
import com.softeno.template.users.db.User
import com.softeno.template.users.event.AppEvent
import com.softeno.template.users.http.dto.*
import com.softeno.template.users.http.reactive.PermissionsReactiveMongoTemplate
import com.softeno.template.users.http.reactive.PermissionsReactiveRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import org.apache.commons.logging.LogFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Repository
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.security.Principal


@Repository
interface PermissionCoroutineRepository :
    CoroutineCrudRepository<Permission, String>, ReactiveQuerydslPredicateExecutor<Permission> {
    fun findAllBy(pageable: Pageable): Flow<Permission>
}

@RestController
@RequestMapping("/coroutine/")
@Validated
class CoroutinePermissionController(
    private val permissionCoroutineRepository: PermissionCoroutineRepository,
    private val permissionsReactiveMongoTemplate: PermissionsReactiveMongoTemplate,
    private val permissionsReactiveRepository: PermissionsReactiveRepository
) {
//    @PreAuthorize(value = "hasRole('admin')")
    @GetMapping("/permissions")
    fun getAllPermissions(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String,
        @AuthenticationPrincipal oauth2User: Mono<OAuth2User>
    ): Flow<PermissionDto> =
        permissionCoroutineRepository.findAllBy(getPageRequest(page, size, sort, direction))
            .map { it.toDto() }


    @GetMapping("/permissions/{id}")
    suspend fun getPermission(@PathVariable id: String): PermissionDto? {
        // note: it can be done by: permissionCoroutineRepository.findById(id) -> Permission?
        val permission = QPermission.permission
        val predicate = permission.id.eq(id)
        return permissionsReactiveRepository.findOne(predicate)
            .asFlow().firstOrNull()?.toDto()
    }

    @DeleteMapping("/permissions/{id}")
    suspend fun deletePermission(@PathVariable id: String) = permissionCoroutineRepository.deleteById(id)

    @PostMapping("/permissions")
    suspend fun createPermission(@RequestBody(required = true) input: PermissionInput): PermissionDto =
        permissionCoroutineRepository.save(input.toDocument()).toDto()

    @PutMapping("/permissions/{id}")
    suspend fun updatePermission(@PathVariable id: String, @RequestBody(required = true) input: PermissionInput): PermissionDto? =
        // note: we're using reactive api as flow, and then we can get the flow element
        permissionsReactiveMongoTemplate.findAndModify(id, input)
            .switchIfEmpty(Mono.error(RuntimeException("Permission: $id Not Found")))
            .asFlow()
            .firstOrNull()?.toDto()
}

@Repository
interface UserCoroutineRepository : CoroutineCrudRepository<User, String> {
    fun findAllBy(pageable: Pageable): Flow<User>
    suspend fun findByIdAndVersion(id: String, version: Long): User?
}

@RestController
@RequestMapping("/coroutine/")
@Validated
class CoroutineUserController(
    val userCoroutineRepository: UserCoroutineRepository,
    val permissionCoroutineRepository: PermissionCoroutineRepository,
    val applicationEventPublisher: ApplicationEventPublisher
) {
    private val log = LogFactory.getLog(javaClass)

    @PostMapping("/users")
    suspend fun createUser(@RequestBody(required = true) input: UserInput, @AuthenticationPrincipal oauth2User: Mono<OAuth2User>): UserDto {
        val permissions: List<Permission> = input.permissionIds.asFlow()
            .map { permissionCoroutineRepository.findById(it)
                ?: throw RuntimeException("error: permission not found")
            }
            .toList()

        return userCoroutineRepository.save(User(id = null, name = input.name, email = input.email,
            permissions = permissions.map { permission -> permission.id!! }.toSet(),
            createdDate = null, createdByUser = null, lastModifiedDate = null, modifiedByUser = null, version = null))
            .also { applicationEventPublisher.publishEvent(AppEvent("USER_CREATED_COROUTINE: ${it.id}")) }
            .toDto(permissions)
    }

    @PutMapping("/users/{id}")
    suspend fun updateUser(@PathVariable id: String, @RequestBody(required = true) input: UserInput): UserDto {
        val version = input.version ?: throw RuntimeException("error: version is required")

        val permissions: List<Permission> = input.permissionIds
            .map { permissionCoroutineRepository.findById(it) ?: throw RuntimeException("error: permission not found") }

        val user = userCoroutineRepository.findByIdAndVersion(id, version) ?: throw RuntimeException("error: user not found")
        return userCoroutineRepository.save(
            user.copy(name = input.name, email = input.email, permissions = permissions.map { it.id!! }.toSet())
        ).toDto(permissions)
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
        return user.toDto(userPermissions)
    }

    @GetMapping("/users")
    suspend fun getAllUsersMapped(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String,
        @AuthenticationPrincipal monoPrincipal: Mono<Principal>,
    ): Flow<UserDto> {
        val principal = monoPrincipal.awaitSingle()
        log.info("principal: $principal, name: ${principal.name}")
        val authentication = ReactiveSecurityContextHolder.getContext().map { it.authentication }.awaitSingle()
        val token = (authentication as JwtAuthenticationToken).token
        val userId = token.claims["sub"]
        val authorities = authentication.authorities
        log.debug("authentication: $authentication")
        log.debug("authorities: $authorities")
        log.debug("token: $token")
        log.debug("token claims: ${token.claims}")
        log.info("keycloak userId: $userId")

        return userCoroutineRepository.findAllBy(getPageRequest(page, size, sort, direction))
            .map { e -> e.toDto(e.permissions.mapNotNull { permissionCoroutineRepository.findById(it) }) }
    }


}