package com.softeno.template.app.permission.mapper

import com.softeno.template.app.permission.Permission
import com.softeno.template.app.permission.PermissionModifyCommand
import com.softeno.template.app.permission.api.PermissionDto
import com.softeno.template.app.permission.db.PermissionDocument

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

fun Permission.toDto(): PermissionDto = PermissionDto(
    id = this.id,
    name = this.name,
    description = this.description,
    version = this.base?.version,
    createdBy = this.base?.createdBy,
    createdDate = this.base?.createdDate,
    modifiedBy = this.base?.modifiedBy,
    modifiedDate = this.base?.modifiedDate
)