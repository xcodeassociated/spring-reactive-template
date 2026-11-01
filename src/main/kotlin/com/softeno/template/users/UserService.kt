package com.softeno.template.users

import com.softeno.template.common.ErrorFactory
import com.softeno.template.common.PrincipalHandler
import com.softeno.template.common.getPageRequest
import com.softeno.template.users.db.UserCoroutineRepository
import com.softeno.template.users.db.UserDocument
import com.softeno.template.users.api.VersionMissingException
import com.softeno.template.users.permission.db.PermissionDocument
import com.softeno.template.users.permission.PermissionService
import io.micrometer.tracing.Tracer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.apache.commons.logging.LogFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.security.Principal

/**
 * The users core (ADR-0003). No domain wrapper classes — services work on the
 * db documents; [UserWithPermissions] is a plain read tuple of documents.
 * The core write path ENDS at [UserEventsPublisher] — everything after Kafka
 * (notifications) is optional and pluggable.
 */
@Service
class UserService(
    private val userCoroutineRepository: UserCoroutineRepository,
    private val permissionService: PermissionService,
    private val userDocumentService: UserDocumentService,
    private val userEventsPublisher: UserEventsPublisher,
    private val tracer: Tracer
) : PrincipalHandler {
    private val log = LogFactory.getLog(javaClass)

    suspend fun getAll(
        page: Int,
        size: Int,
        sort: String,
        direction: String,
        monoPrincipal: Mono<Principal>
    ): Flow<UserWithPermissions> {
        showPrincipal(log, monoPrincipal)
        val userDocs = userCoroutineRepository.findAllBy(getPageRequest(page, size, sort, direction)).toList()
        val permissionsByUser = userDocumentService.getUserAndPermissions(userDocs)

        // debug only
        val traceId = tracer.currentSpan()?.context()?.traceId()
        val mdc = MDC.get("traceId")
        val mdcSpan = MDC.get("spanId")
        log.debug("Show traceId=$traceId, mdcTraceId=$mdc and mdcSpanId=$mdcSpan")

        return userDocs.map { UserWithPermissions(it, permissionsByUser[it] ?: emptyList()) }.asFlow()
    }

    suspend fun get(id: String): UserWithPermissions = withContext(MDCContext()) {
        val userDoc = userDocumentService.get(id)
        val permissions = userDocumentService.getUserAndPermissions(listOf(userDoc))[userDoc] ?: emptyList()
        UserWithPermissions(userDoc, permissions)
    }

    suspend fun create(input: UserModifyCommand): UserWithPermissions = withContext(MDCContext()) {
        log.info("Create user with: $input")

        val permissions = input.permissionIds.asFlow()
            .map { permissionService.get(it) }
            .toList()

        val saved = userCoroutineRepository.save(
            UserDocument(
                id = null,
                createdDate = null,
                createdByUser = null,
                modifiedByUser = null,
                lastModifiedDate = null,
                version = null,
                name = input.name,
                email = input.email,
                permissions = permissions.mapNotNull { it.id }.toSet()
            )
        )
        log.info("User created: $saved")

        // end of the core part: the integration event is the boundary
        userEventsPublisher.publishUserCreated(saved.id!!)
        UserWithPermissions(saved, permissions)
    }

    suspend fun update(id: String, input: UserModifyCommand): UserWithPermissions = withContext(MDCContext()) {
        val version = input.version ?: throw VersionMissingException("Version is required")

        val permissions: List<PermissionDocument> = input.permissionIds.map { permissionService.get(it) }

        val user = userDocumentService.get(id, version)
        UserWithPermissions(
            userCoroutineRepository.save(
                user.copy(name = input.name, email = input.email, permissions = permissions.mapNotNull { it.id }.toSet())
            ),
            permissions
        )
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
                    it.permissions.flatMap { id -> permissions.filter { e -> e.id == id } }
                )
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

/** Read tuple of documents — what the read side returns; no domain wrapper. */
data class UserWithPermissions(val user: UserDocument, val permissions: List<PermissionDocument>)
