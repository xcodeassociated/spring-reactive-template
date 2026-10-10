package com.softeno.template.app.permission

import com.softeno.template.app.common.ErrorFactory
import com.softeno.template.app.common.getPageRequest
import com.softeno.template.app.permission.api.reactive.PermissionsReactiveMongoTemplate
import com.softeno.template.app.permission.db.PermissionCoroutineRepository
import com.softeno.template.app.permission.db.PermissionDocument
import com.softeno.template.app.permission.db.PermissionsReactiveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.apache.commons.logging.LogFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono


@Service
class PermissionService(
    private val permissionCoroutineRepository: PermissionCoroutineRepository,
    private val permissionsReactiveMongoTemplate: PermissionsReactiveMongoTemplate,
    private val permissionsReactiveRepository: PermissionsReactiveRepository
) {
    private val log = LogFactory.getLog(javaClass)

    suspend fun getAll(page: Int, size: Int, sort: String, direction: String): Flow<Permission> =
        withContext(MDCContext()) {
            // note: trigger graphql error handler
            // throw RuntimeException("xd")
            permissionCoroutineRepository.findAllBy(getPageRequest(page, size, sort, direction)).map { it.toDomain() }
        }

    suspend fun get(ids: Set<String>): Flow<Permission> = withContext(MDCContext()) {
        permissionCoroutineRepository.findAllById(ids).map { it.toDomain() }
    }

    suspend fun get(id: String): Permission =
        withContext(MDCContext()) {
            return@withContext permissionsReactiveRepository.findById(id).map { it.toDomain() }.awaitSingle()
        }

    suspend fun update(id: String, input: PermissionModifyCommand): Permission =
        withContext(MDCContext()) {
            // note: we're using reactive api as flow, and then we can get the flow element
            permissionsReactiveMongoTemplate.findAndModify(id, input)
                .switchIfEmpty(Mono.error(ErrorFactory.errorPermissionNotFound(id)))
                .map { it.toDomain() }
                .awaitSingle()
        }

    suspend fun create(input: PermissionModifyCommand) = withContext(MDCContext()) {
        permissionCoroutineRepository.save(input.toDocument()).toDomain()
    }

    suspend fun delete(id: String) = withContext(MDCContext()) {
        permissionCoroutineRepository.deleteById(id)
    }
}

fun PermissionDocument.toDomain() =
    Permission(id = this.id, base = this, name = this.name, description = this.description)

fun Permission.toDocument() = if (this.base != null) {
    this.base as PermissionDocument
} else {
    PermissionDocument(
        id = this.id,
        createdByUser = null,
        createdDate = null,
        lastModifiedDate = null,
        modifiedByUser = null,
        version = null,
        name = this.name,
        description = this.description
    )
}

fun PermissionModifyCommand.toDocument() = PermissionDocument(
    id = null, name = this.name, description = this.description,
    createdDate = null, createdByUser = null, lastModifiedDate = null, modifiedByUser = null, version = this.version
)