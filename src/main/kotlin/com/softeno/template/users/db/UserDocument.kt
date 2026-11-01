package com.softeno.template.users.db

import com.softeno.template.common.BaseDocument
import com.softeno.template.users.permission.db.PermissionDocument
import org.springframework.data.annotation.*
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import kotlin.reflect.KClass

@Document
data class UserDocument(
    @Id
    override val id: String?,

    @CreatedDate
    override var createdDate: LocalDateTime?,

    @CreatedBy
    var createdByUser: String?,

    @LastModifiedBy
    var modifiedByUser: String?,

    @LastModifiedDate
    var lastModifiedDate: LocalDateTime?,

    @Version
    override var version: Long?,

    val name: String,

    @Indexed(unique = true)
    val email: String,

    /**
     * Embedded permission snapshots (ADR-0005): reads never join, and
     * `UserPermissionSync` propagates permission updates/deletes into every
     * user holding a copy. A dedicated plain type — embedding PermissionDocument
     * would leak its @Indexed/@Id mapping annotations into the user collection.
     */
    val permissions: Set<PermissionSnapshot>
) : BaseDocument {
    @get:Transient
    override val createdBy: String?
        get() = createdByUser

    @get:Transient
    override val modifiedBy: String?
        get() = modifiedByUser

    @get:Transient
    override val modifiedDate: LocalDateTime?
        get() = lastModifiedDate

    @get:Transient
    override val clazz: KClass<out UserDocument>
        get() = this::class
}

/**
 * Write-time copy of a permission, embedded in [UserDocument.permissions].
 * The id field is deliberately named `permissionId`: a property named `id` would
 * be treated as the nested document's _id and the fan-out query could not address
 * it as a plain string (ADR-0005).
 */
data class PermissionSnapshot(
    val permissionId: String,
    val name: String,
    val description: String,
    val version: Long?,
    val createdBy: String?,
    val createdDate: LocalDateTime?,
    val modifiedBy: String?,
    val modifiedDate: LocalDateTime?,
) {
    /** Convenience accessor for GraphQL/DTO mappings — not persisted (no backing field). */
    val id: String
        get() = permissionId

    companion object {
        fun of(permission: PermissionDocument) = PermissionSnapshot(
            permissionId = permission.id!!,
            name = permission.name,
            description = permission.description,
            version = permission.version,
            createdBy = permission.createdBy,
            createdDate = permission.createdDate,
            modifiedBy = permission.modifiedBy,
            modifiedDate = permission.modifiedDate,
        )
    }
}
