package com.softeno.template.users

import com.softeno.template.common.ErrorFactory
import com.softeno.template.common.getPageRequest
import com.softeno.template.users.api.VersionMissingException
import com.softeno.template.users.db.PermissionSnapshot
import com.softeno.template.users.db.UserDocument
import com.softeno.template.users.db.UserRepository
import com.softeno.template.users.permission.PermissionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.apache.commons.logging.LogFactory
import org.springframework.stereotype.Service

/**
 * The users core (ADR-0003). Services work on the db documents — permissions
 * are embedded snapshots (ADR-0005), so every read is a single document, no joins.
 * The write path ENDS at [UserEventsPublisher] — everything after Kafka
 * (notifications) is optional and pluggable.
 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val permissionService: PermissionService,
    private val userEventsPublisher: UserEventsPublisher,
) {
    private val log = LogFactory.getLog(javaClass)

    suspend fun getAll(page: Int, size: Int, sort: String, direction: String): Flow<UserDocument> =
        withContext(MDCContext()) {
            userRepository.findAllBy(getPageRequest(page, size, sort, direction))
        }

    suspend fun get(id: String): UserDocument = withContext(MDCContext()) {
        userRepository.findById(id) ?: throw ErrorFactory.errorUserNotFound(id)
    }

    suspend fun create(input: UserModifyCommand): UserDocument = withContext(MDCContext()) {
        log.info("Create user with: $input")
        // get() per id validates existence — an unknown permission id fails the create
        val permissions = input.permissionIds.map { PermissionSnapshot.of(permissionService.get(it)) }.toSet()

        val saved = userRepository.save(
            UserDocument(
                id = null,
                createdDate = null,
                createdByUser = null,
                modifiedByUser = null,
                lastModifiedDate = null,
                version = null,
                name = input.name,
                email = input.email,
                permissions = permissions
            )
        )
        log.info("User created: $saved")

        // end of the core part: the integration event is the boundary
        userEventsPublisher.publish(saved.id!!, UserAction.CREATED)
        saved
    }

    suspend fun update(id: String, input: UserModifyCommand): UserDocument = withContext(MDCContext()) {
        val version = input.version ?: throw VersionMissingException("Version is required")
        val permissions = input.permissionIds.map { PermissionSnapshot.of(permissionService.get(it)) }.toSet()
        val user = userRepository.findByIdAndVersion(id, version) ?: throw ErrorFactory.errorUserNotFound(id)
        val saved = userRepository.save(user.copy(name = input.name, email = input.email, permissions = permissions))
        userEventsPublisher.publish(saved.id!!, UserAction.UPDATED)
        saved
    }

    suspend fun delete(id: String) = withContext(MDCContext()) {
        if (!userRepository.existsById(id)) {
            throw ErrorFactory.errorUserNotFound(id)
        }
        userRepository.deleteById(id)
        userEventsPublisher.publish(id, UserAction.DELETED)
    }

    suspend fun size(): Long = withContext(MDCContext()) { userRepository.count() }
}
