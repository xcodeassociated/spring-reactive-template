package com.softeno.template.users.permission.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.softeno.template.users.permission.db.PermissionDocument
import java.time.LocalDateTime

data class PermissionDto(
    @param:JsonProperty("_id")
    val id: String?,
    val name: String,
    val description: String,
    val version: Long?,
    val createdBy: String?,
    val createdDate: LocalDateTime?,
    val modifiedBy: String?,
    val modifiedDate: LocalDateTime?
)

fun PermissionDocument.toDto(): PermissionDto = PermissionDto(
    id = this.id,
    name = this.name,
    description = this.description,
    version = this.version,
    createdBy = this.createdBy,
    createdDate = this.createdDate,
    modifiedBy = this.modifiedBy,
    modifiedDate = this.modifiedDate
)
