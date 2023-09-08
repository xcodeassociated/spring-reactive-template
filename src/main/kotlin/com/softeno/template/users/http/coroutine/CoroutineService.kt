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

    fun getAll(page: Int, size: Int, sort: String, direction: String): Flow<PermissionDto> =
        permissionCoroutineRepository.findAllBy(getPageRequest(page, size, sort, direction))
            .map { it.toDto() }

    suspend fun get(id: String): PermissionDto? {
        // note: it can be done by: permissionCoroutineRepository.findById(id) -> Permission?
        val permission = QPermission.permission
        val predicate = permission.id.eq(id)
        return permissionsReactiveRepository.findOne(predicate)
            .asFlow().firstOrNull()?.toDto()
    }

    suspend fun update(id: String, input: PermissionInput) =
        // note: we're using reactive api as flow, and then we can get the flow element
        permissionsReactiveMongoTemplate.findAndModify(id, input)
            .switchIfEmpty(Mono.error(RuntimeException("Permission: $id Not Found")))
            .asFlow()
            .firstOrNull()
            ?.toDto()

    suspend fun create(input: PermissionInput) =
        permissionCoroutineRepository.save(input.toDocument())
            .toDto()

    suspend fun delete(id: String) = permissionCoroutineRepository.deleteById(id)

}

@Service
final class CoroutineUserService(
    val userCoroutineRepository: UserCoroutineRepository,
    val permissionCoroutineRepository: PermissionCoroutineRepository,
    val applicationEventPublisher: ApplicationEventPublisher
) {
    private val log = LogFactory.getLog(javaClass)

    suspend fun getAll(page: Int, size: Int, sort: String, direction: String, monoPrincipal: Mono<Principal>): Flow<UserDto> {
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


    suspend fun get(id: String): UserDto? {
        val user: User = userCoroutineRepository.findById(id) ?: return null
        val userPermissions = user.permissions.asFlow()
            .map { permissionCoroutineRepository.findById(it) }
            .filterNotNull()
            .toList()
        return user.toDto(userPermissions)
    }

    suspend fun create(input: UserInput): UserDto {
        val permissions: List<Permission> = input.permissionIds.asFlow()
            .map { permissionCoroutineRepository.findById(it)
                ?: throw RuntimeException("error: permission not found")
            }
            .toList()

        return userCoroutineRepository.save(
            User(id = null, name = input.name, email = input.email,
            permissions = permissions.map { permission -> permission.id!! }.toSet(),
            createdDate = null, createdByUser = null, lastModifiedDate = null, modifiedByUser = null, version = null)
        )
            .also { applicationEventPublisher.publishEvent(AppEvent("USER_CREATED_COROUTINE: ${it.id}")) }
            .toDto(permissions)
    }

    suspend fun update(id: String, input: UserInput): UserDto {
        val version = input.version ?: throw RuntimeException("error: version is required")

        val permissions: List<Permission> = input.permissionIds
            .map { permissionCoroutineRepository.findById(it) ?: throw RuntimeException("error: permission not found") }

        val user = userCoroutineRepository.findByIdAndVersion(id, version) ?: throw RuntimeException("error: user not found")
        return userCoroutineRepository.save(
            user.copy(name = input.name, email = input.email, permissions = permissions.map { it.id!! }.toSet())
        ).toDto(permissions)
    }

    suspend fun delete(id: String) {
        if (!userCoroutineRepository.existsById(id)) {
            throw RuntimeException("error: user does not exists")
        }
        userCoroutineRepository.deleteById(id)
    }

    suspend fun size(): Long = userCoroutineRepository.count()
}
