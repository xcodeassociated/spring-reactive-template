package com.softeno.template.app.user.mapper

import com.softeno.template.app.permission.Permission
import com.softeno.template.app.permission.mapper.toDto
import com.softeno.template.app.user.User
import com.softeno.template.app.user.api.UserDto
import com.softeno.template.app.user.db.UserDocument

fun UserDocument.toDomain(permissions: List<Permission>?) =
    User(id = this.id, base = this, name = this.name, email = this.email, permissions = permissions)

fun User.toDocument() = if (this.base != null) {
    this.base as UserDocument
} else {
    UserDocument(
        id = this.id,
        name = this.name,
        email = this.email,
        permissions = this.permissions?.map { it.id!! }?.toSet() ?: setOf(),
        createdDate = null,
        createdByUser = null,
        modifiedByUser = null,
        lastModifiedDate = null,
        version = null
    )
}

fun User.toDto(): UserDto = UserDto(
    id = this.id!!,
    name = this.name,
    email = this.email,
    permissions = permissions?.map { it.toDto() },
    version = this.base?.version,
    createdBy = this.base?.createdBy,
    createdDate = this.base?.createdDate,
    modifiedBy = this.base?.modifiedBy,
    modifiedDate = this.base?.modifiedDate
)