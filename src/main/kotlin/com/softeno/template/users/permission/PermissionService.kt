package com.softeno.template.users.permission

import com.softeno.template.common.ErrorFactory
import com.softeno.template.common.getPageRequest
import com.softeno.template.users.permission.db.PermissionsReactiveMongoTemplate
import com.softeno.template.users.permission.db.PermissionCoroutineRepository
import com.softeno.template.users.permission.db.PermissionDocument
import com.softeno.template.users.permission.db.PermissionsReactiveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.apache.commons.logging.LogFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/**
 * Permission service (users core). Works on the db documents — no domain wrapper
 * classes (see ADR-0003); the api layer maps documents to DTOs.
 */
@Service
class PermissionService(
    private val permissionCoroutineRepository: PermissionCoroutineRepository,
    private val permissionsReactiveMongoTemplate: PermissionsReactiveMongoTemplate,
    private val permissionsReactiveRepository: PermissionsReactiveRepository
) {
    private val log = LogFactory.getLog(javaClass)

    suspend fun getAll(page: Int, size: Int, sort: String, direction: String): Flow<PermissionDocument> =
        withContext(MDCContext()) {
            // note: trigger graphql error handler
            // throw RuntimeException("xd")
            permissionCoroutineRepository.findAllBy(getPageRequest(page, size, sort, direction))
        }

    suspend fun get(ids: Set<String>): Flow<PermissionDocument> = withContext(MDCContext()) {
        permissionCoroutineRepository.findAllById(ids)
    }

    suspend fun get(id: String): PermissionDocument =
        withContext(MDCContext()) {
            return@withContext permissionsReactiveRepository.findById(id).awaitSingle()
        }

    suspend fun update(id: String, input: PermissionModifyCommand): PermissionDocument =
        withContext(MDCContext()) {
            // note: we're using reactive api as flow, and then we can get the flow element
            permissionsReactiveMongoTemplate.findAndModify(id, input)
                .switchIfEmpty(Mono.error(ErrorFactory.errorPermissionNotFound(id)))
                .awaitSingle()
        }

    suspend fun create(input: PermissionModifyCommand) = withContext(MDCContext()) {
        permissionCoroutineRepository.save(input.toDocument())
    }

    suspend fun delete(id: String) = withContext(MDCContext()) {
        permissionCoroutineRepository.deleteById(id)
    }
}

fun PermissionModifyCommand.toDocument() = PermissionDocument(
    id = null, name = this.name, description = this.description,
    createdDate = null, createdByUser = null, lastModifiedDate = null, modifiedByUser = null, version = this.version
)
