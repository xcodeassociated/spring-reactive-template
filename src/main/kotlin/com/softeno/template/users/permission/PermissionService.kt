package com.softeno.template.users.permission

import com.softeno.template.common.ErrorFactory
import com.softeno.template.common.getPageRequest
import com.softeno.template.users.db.UserPermissionSync
import com.softeno.template.users.permission.db.PermissionDocument
import com.softeno.template.users.permission.db.PermissionRepository
import com.softeno.template.users.permission.db.PermissionsReactiveMongoTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

/**
 * Permission service (users core). Works on the db documents — no domain wrapper
 * classes (ADR-0003); the api layer maps documents to DTOs.
 */
@Service
class PermissionService(
    private val permissionRepository: PermissionRepository,
    private val permissionsReactiveMongoTemplate: PermissionsReactiveMongoTemplate,
    private val userPermissionSync: UserPermissionSync,
) {
    suspend fun getAll(page: Int, size: Int, sort: String, direction: String): Flow<PermissionDocument> =
        withContext(MDCContext()) {
            permissionRepository.findAllBy(getPageRequest(page, size, sort, direction))
        }

    suspend fun get(ids: Set<String>): Flow<PermissionDocument> = withContext(MDCContext()) {
        permissionRepository.findAllById(ids)
    }

    suspend fun get(id: String): PermissionDocument = withContext(MDCContext()) {
        permissionRepository.findById(id) ?: throw ErrorFactory.errorPermissionNotFound(id)
    }

    suspend fun update(id: String, input: PermissionModifyCommand): PermissionDocument =
        withContext(MDCContext()) {
            // optimistic-locking findAndModify: version must match, empty -> not found
            val updated = permissionsReactiveMongoTemplate.findAndModify(id, input)
                .awaitSingleOrNull()
                ?: throw ErrorFactory.errorPermissionNotFound(id)
            // embedded snapshots in users are snapshots (ADR-0005) — fan the change out
            userPermissionSync.syncPermission(updated)
            updated
        }

    suspend fun create(input: PermissionModifyCommand): PermissionDocument = withContext(MDCContext()) {
        permissionRepository.save(input.toDocument())
    }

    suspend fun delete(id: String) = withContext(MDCContext()) {
        permissionRepository.deleteById(id)
        // drop the embedded copies so no user keeps a dangling reference
        userPermissionSync.pullPermission(id)
    }
}
