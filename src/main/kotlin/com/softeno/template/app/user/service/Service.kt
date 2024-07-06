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
import io.micrometer.tracing.Tracer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.apache.commons.logging.LogFactory
import org.slf4j.MDC
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.security.Principal


@Service
class UserService(
    private val userCoroutineRepository: UserCoroutineRepository,
    private val permissionService: PermissionService,
    private val userDocumentService: UserDocumentService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val tracer: Tracer
) : PrincipalHandler {
    private val log = LogFactory.getLog(javaClass)

    // note: used by http rest controller to return users with mapped permissions
//    @ContinueSpan
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

        // debug only
        val traceId = tracer.currentSpan()?.context()?.traceId()
        val mdc = MDC.get("traceId")
        val mdcSpan = MDC.get("spanId")
        log.debug("Show traceId=$traceId, mdcTraceId=$mdc and mdcSpanId=$mdcSpan")

        return users.asFlow()
    }

    suspend fun get(id: String): User = withContext(MDCContext()) {
        val userDoc = userDocumentService.get(id)
        val result = userDocumentService.getUserAndPermissions(listOf(userDoc))
        return@withContext userDoc.toDomain(result[userDoc]?.map { it.toDomain() })
    }

    suspend fun create(input: UserModifyCommand): User = withContext(MDCContext()) {
        log.info("Create user with: $input")

        val permissions = input.permissionIds.asFlow()
            .map { permissionService.get(it) }
            .toList()

        val user = userCoroutineRepository.save(User(input, permissions).toDocument()).toDomain(permissions)
        log.info("User created: $user")

        applicationEventPublisher.publishEvent(
            AppEvent("USER_CREATED: ${user.id}", traceId = MDC.get("traceId"), spanId = MDC.get("spanId"))
        )
        return@withContext user
    }

    suspend fun update(id: String, input: UserModifyCommand): User = withContext(MDCContext()) {
        val version = input.version ?: throw VersionMissingException("Version is required")

        val permissions: List<Permission> = input.permissionIds.map { permissionService.get(it) }

        val user = userDocumentService.get(id, version)
        return@withContext userCoroutineRepository.save(
            user.copy(name = input.name, email = input.email, permissions = permissions.map { it.id!! }.toSet())
        ).toDomain(permissions)
    }

    suspend fun delete(id: String) = withContext(MDCContext()) { userDocumentService.delete(id) }

    suspend fun size(): Long = withContext(MDCContext()) { userCoroutineRepository.count() }

}

@Service
class UserDocumentService(
    private val permissionService: PermissionService,
    private val userCoroutineRepository: UserCoroutineRepository,
) : PrincipalHandler {
    private val log = LogFactory.getLog(javaClass)

    suspend fun getUserAndPermissions(users: List<UserDocument>): Map<UserDocument, List<PermissionDocument>> =
        withContext(MDCContext()) {
            val permissionIds = users.map { it.permissions }.flatten().toSet()
            val permissions = permissionService.get(permissionIds).toList()
            val pairs = users.map {
                Pair(
                    it,
                    it.permissions.flatMap { permissions.map { it.toDocument() }.filter { e -> e.id == it } })
            }
            return@withContext pairs.associate { it }
    }

    // note: used by graphql controller to return users without mapped permissions, permissions will be mapped by batch
    suspend fun getUsersWithoutPermissions(
        page: Int,
        size: Int,
        sort: String,
        direction: String,
        monoPrincipal: Mono<Principal>
    ): Flow<UserDocument> = withContext(MDCContext()) {
        showPrincipal(log, monoPrincipal)
        return@withContext userCoroutineRepository.findAllBy(getPageRequest(page, size, sort, direction))
    }

    suspend fun get(id: String): UserDocument = withContext(MDCContext()) {
        userCoroutineRepository.findById(id) ?: throw ErrorFactory.errorUserNotFound(id)
    }

    suspend fun get(id: String, version: Long) = withContext(MDCContext()) {
        userCoroutineRepository.findByIdAndVersion(id, version) ?: throw ErrorFactory.errorUserNotFound(id)
    }

    suspend fun delete(id: String) = withContext(MDCContext()) {
        if (!userCoroutineRepository.existsById(id)) {
            throw ErrorFactory.errorUserNotFound(id)
        }
        return@withContext userCoroutineRepository.deleteById(id)
    }
}
