package com.softeno.template.app.user.service

import com.softeno.template.app.common.ErrorFactory
import com.softeno.template.app.common.PrincipalHandler
import com.softeno.template.app.common.getPageRequest
import com.softeno.template.app.event.AppEvent
import com.softeno.template.app.permission.Permission
import com.softeno.template.app.permission.db.PermissionDocument
import com.softeno.template.app.permission.mapper.toDocument
import com.softeno.template.app.permission.mapper.toDomain
import com.softeno.template.app.permission.service.PermissionService
import com.softeno.template.app.user.User
import com.softeno.template.app.user.UserModifyCommand
import com.softeno.template.app.user.api.VersionMissingException
import com.softeno.template.app.user.db.UserCoroutineRepository
import com.softeno.template.app.user.db.UserDocument
import com.softeno.template.app.user.mapper.toDocument
import com.softeno.template.app.user.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.apache.commons.logging.LogFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.security.Principal


@Service
class UserService(
    private val userCoroutineRepository: UserCoroutineRepository,
    private val permissionService: PermissionService,
    private val userDocumentService: UserDocumentService,
    private val applicationEventPublisher: ApplicationEventPublisher
) : PrincipalHandler {
    private val log = LogFactory.getLog(javaClass)

    // note: used by http rest controller to return users with mapped permissions
    suspend fun getAll(
        page: Int,
        size: Int,
        sort: String,
        direction: String,
        monoPrincipal: Mono<Principal>
    ): Flow<User> {
        showPrincipal(log, monoPrincipal)
        val userDocs = userCoroutineRepository.findAllBy(getPageRequest(page, size, sort, direction)).toList()
        val userAndPermissionsDocs = userDocumentService.getUserAndPermissions(userDocs)
        val users = userAndPermissionsDocs.entries.map { it.key.toDomain(it.value.map { e -> e.toDomain() }) }
        return users.asFlow()
    }

    suspend fun get(id: String): User {
        val userDoc = userDocumentService.get(id)
        val result = userDocumentService.getUserAndPermissions(listOf(userDoc))
        return userDoc.toDomain(result[userDoc]?.map { it.toDomain() })
    }

    suspend fun create(input: UserModifyCommand): User {
        val permissions = input.permissionIds.asFlow()
            .map { permissionService.get(it) }
            .toList()

        val user = userCoroutineRepository.save(User(input, permissions).toDocument()).toDomain(permissions)
        applicationEventPublisher.publishEvent(AppEvent("USER_CREATED_COROUTINE: ${user.id}"))
        return user
    }

    suspend fun update(id: String, input: UserModifyCommand): User {
        val version = input.version ?: throw VersionMissingException("Version is required")

        val permissions: List<Permission> = input.permissionIds.map { permissionService.get(it) }

        val user = userDocumentService.get(id, version)
        return userCoroutineRepository.save(
            user.copy(name = input.name, email = input.email, permissions = permissions.map { it.id!! }.toSet())
        ).toDomain(permissions)
    }

    suspend fun delete(id: String) = userDocumentService.delete(id)

    suspend fun size(): Long = userCoroutineRepository.count()

}

@Service
final class UserDocumentService(
    private val permissionService: PermissionService,
    private val userCoroutineRepository: UserCoroutineRepository,
) : PrincipalHandler {
    private val log = LogFactory.getLog(javaClass)

    suspend fun getUserAndPermissions(users: List<UserDocument>): Map<UserDocument, List<PermissionDocument>> {
        val permissionIds = users.map { it.permissions }.flatten().toSet()
        val permissions = permissionService.get(permissionIds).toList()
        val pairs = users.map {
            Pair(
                it,
                it.permissions.flatMap { permissions.map { it.toDocument() }.filter { e -> e.id == it } })
        }
        return pairs.associate { it }
    }

    // note: used by graphql controller to return users without mapped permissions, permissions will be mapped by batch
    suspend fun getUsersWithoutPermissions(
        page: Int,
        size: Int,
        sort: String,
        direction: String,
        monoPrincipal: Mono<Principal>
    ): Flow<UserDocument> {
        showPrincipal(log, monoPrincipal)
        return userCoroutineRepository.findAllBy(getPageRequest(page, size, sort, direction))
    }

    suspend fun get(id: String): UserDocument =
        userCoroutineRepository.findById(id) ?: throw ErrorFactory.errorUserNotFound(id)

    suspend fun get(id: String, version: Long) =
        userCoroutineRepository.findByIdAndVersion(id, version) ?: throw ErrorFactory.errorUserNotFound(id)

    suspend fun delete(id: String) {
        if (!userCoroutineRepository.existsById(id)) {
            throw ErrorFactory.errorUserNotFound(id)
        }
        userCoroutineRepository.deleteById(id)
    }
}