package com.softeno.template.users.permission

import com.softeno.template.users.permission.db.PermissionDocument

/**
 * Create/update input for permissions (works directly on PermissionDocument).
 * `description` is nullable because the GraphQL contract says so
 * (`PermissionInput.description: String`); the document stores `""` for null.
 */
data class PermissionModifyCommand(
    val name: String,
    val description: String?,
    val version: Long?
)

fun PermissionModifyCommand.toDocument() = PermissionDocument(
    id = null, name = this.name, description = this.description ?: "",
    createdDate = null, createdByUser = null, lastModifiedDate = null, modifiedByUser = null, version = this.version
)
