package com.softeno.template.users.http.coroutine

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
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.security.Principal


@Service
class CoroutinePermissionService(
    private val permissionCoroutineRepository: PermissionCoroutineRepository,
    private val permissionsReactiveMongoTemplate: PermissionsReactiveMongoTemplate,
    private val permissionsReactiveRepository: PermissionsReactiveRepository
) {
    private val log = LogFactory.getLog(javaClass)

    fun getAll(page: Int, size: Int, sort: String, direction: String): Flow<Permission> =
        // note: trigger graphql error handler
//         throw RuntimeException("xd")
        permissionCoroutineRepository.findAllBy(getPageRequest(page, size, sort, direction))

    fun get(ids: Set<String>): Flow<Permission> = permissionCoroutineRepository.findAllById(ids)

    suspend fun get(id: String): Permission {
        // note: it can be done by: permissionCoroutineRepository.findById(id) -> Permission?
        val permission = QPermission.permission
        val predicate = permission.id.eq(id)
        return permissionsReactiveRepository.findOne(predicate)
            .asFlow().firstOrNull() ?: throw errorPermissionNotFound(id)
    }

    suspend fun update(id: String, input: PermissionInput) =
        // note: we're using reactive api as flow, and then we can get the flow element
        permissionsReactiveMongoTemplate.findAndModify(id, input)
            .switchIfEmpty(Mono.error(errorPermissionNotFound(id)))
            .asFlow()
            .firstOrNull()
            ?.toDto()

    suspend fun create(input: PermissionInput) =
        permissionCoroutineRepository.save(input.toDocument())

    suspend fun delete(id: String) = permissionCoroutineRepository.deleteById(id)

    private fun errorPermissionNotFound(id: String): PermissionNotFoundException {
        val message = "Permission not found with id: $id"
        log.warn(message)

        return PermissionNotFoundException(message)
    }
}

data class UserBundle(val user: User, val permissions: List<Permission>)

@Service
final class CoroutineUserService(
    private val userCoroutineRepository: UserCoroutineRepository,
    private val permissionService: CoroutinePermissionService,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    private val log = LogFactory.getLog(javaClass)

    suspend fun getUserAndPermissions(users: List<User>): Map<User, List<Permission>> {
        val permissionIds = users.map { it.permissions }.flatten().toSet()
        val permissions = permissionService.get(permissionIds).toList()
        val pairs = users.map { Pair(it, it.permissions.flatMap { permissions.filter { permission: Permission -> permission.id == it } }) }
        return pairs.associate { it }
    }

    // note: used by http rest controller to return users with mapped permissions
    suspend fun getAll(page: Int, size: Int, sort: String, direction: String, monoPrincipal: Mono<Principal>): Flow<UserDto> {
        showPrincipal(monoPrincipal)
        val users = userCoroutineRepository.findAllBy(getPageRequest(page, size, sort, direction)).toList()
        val userAndPermissions = getUserAndPermissions(users)
        return userAndPermissions.entries.map { it.key.toDto(it.value) }.asFlow()

        // todo: remove n+1
//        return userCoroutineRepository.findAllBy(getPageRequest(page, size, sort, direction))
//            .map { e -> e.toDto(e.permissions.map { permissionService.get(it) }) }
    }

    // note: used by graphql controller to return users without mapped permissions, permissions will be mapped by batch
    suspend fun getUsersWithoutPermissions(page: Int, size: Int, sort: String, direction: String, monoPrincipal: Mono<Principal>): Flow<User> {
        showPrincipal(monoPrincipal)
        return userCoroutineRepository.findAllBy(getPageRequest(page, size, sort, direction))
    }

    suspend fun get(id: String): User =
        userCoroutineRepository.findById(id) ?: throw errorUserNotFound(id)

    suspend fun getUserPermissions(user: User): Flow<Permission> =
        user.permissions.asFlow()
            .map { permissionService.get(it) }
            .filterNotNull()

    suspend fun create(input: UserInput): UserBundle {
        val permissions: List<Permission> = input.permissionIds.asFlow()
            .map { permissionService.get(it) }
            .toList()

        val user = userCoroutineRepository.save(
            User(id = null, name = input.name, email = input.email,
                permissions = permissions.map { permission -> permission.id!! }.toSet(),
                createdDate = null, createdByUser = null, lastModifiedDate = null, modifiedByUser = null, version = null)
        )
            .also { applicationEventPublisher.publishEvent(AppEvent("USER_CREATED_COROUTINE: ${it.id}")) }

        return UserBundle(user, permissions)
    }

    suspend fun update(id: String, input: UserInput): UserBundle {
        val version = input.version ?: throw VersionMissingException("Version is required")

        val permissions: List<Permission> = input.permissionIds.map { permissionService.get(it) }

        val user = userCoroutineRepository.findByIdAndVersion(id, version) ?: throw errorUserNotFound(id)
        val savedUser = userCoroutineRepository.save(
            user.copy(name = input.name, email = input.email, permissions = permissions.map { it.id!! }.toSet())
        )
        return UserBundle(savedUser, permissions)
    }

    suspend fun delete(id: String) {
        if (!userCoroutineRepository.existsById(id)) {
            throw errorUserNotFound(id)
        }
        userCoroutineRepository.deleteById(id)
    }

    suspend fun size(): Long = userCoroutineRepository.count()

    private fun errorUserNotFound(id: String): UserNotFoundException {
        val message = "User not found with id: $id"
        log.warn(message)

        return UserNotFoundException(message)
    }

    private suspend fun showPrincipal(monoPrincipal: Mono<Principal>) {
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
    }
}
