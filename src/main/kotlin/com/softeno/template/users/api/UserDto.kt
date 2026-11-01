package com.softeno.template.users.api

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.softeno.template.users.db.PermissionSnapshot
import com.softeno.template.users.db.UserDocument
import com.softeno.template.users.permission.api.PermissionDto
import java.time.LocalDateTime

data class UserDto(
    @param:JsonProperty("_id")
    val id: String,
    val name: String,
    val email: String,
    @param:JsonProperty("role")
    @param:JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val permissions: List<PermissionDto>?,
    val version: Long?,
    val createdBy: String?,
    val createdDate: LocalDateTime?,
    val modifiedBy: String?,
    val modifiedDate: LocalDateTime?
)

fun UserDocument.toDto(): UserDto = UserDto(
    id = this.id!!,
    name = this.name,
    email = this.email,
    permissions = this.permissions.map { it.toDto() },
    version = this.version,
    createdBy = this.createdBy,
    createdDate = this.createdDate,
    modifiedBy = this.modifiedBy,
    modifiedDate = this.modifiedDate
)

fun PermissionSnapshot.toDto(): PermissionDto = PermissionDto(
    id = this.permissionId,
    name = this.name,
    description = this.description,
    version = this.version,
    createdBy = this.createdBy,
    createdDate = this.createdDate,
    modifiedBy = this.modifiedBy,
    modifiedDate = this.modifiedDate
)
