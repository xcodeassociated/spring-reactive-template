package com.softeno.template.users.http.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.softeno.template.users.db.Permission
import com.softeno.template.users.db.User
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.LocalDateTime

data class PermissionInput(
    val name: String,
    val description: String,
    val version: Long?
)

data class PermissionDto(
    @JsonProperty("_id")
    val id: String?,
    val name: String,
    val description: String,
    val version: Long?,
    val createdBy: String?,
    val createdDate: LocalDateTime?,
    val modifiedBy: String?,
    val modifiedDate: LocalDateTime?
)
data class UserInput(
    val name: String,
    val email: String,
    @JsonProperty("role")
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val permissionIds: Set<String>,
    val version: Long?
)

data class UserDto(
    @JsonProperty("_id")
    val id: String,
    val name: String,
    val email: String,
    @JsonProperty("role")
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val permissions: List<PermissionDto>?,
    val version: Long?,
    val createdBy: String?,
    val createdDate: LocalDateTime?,
    val modifiedBy: String?,
    val modifiedDate: LocalDateTime?
)

data class UserUnmappedPermissionsDto(
    @JsonProperty("_id")
    val id: String,
    val name: String,
    val email: String,
    @JsonProperty("role")
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val permissions: Set<String>,
    val version: Long?,
    val createdBy: String?,
    val createdDate: LocalDateTime?,
    val modifiedBy: String?,
    val modifiedDate: LocalDateTime?
)

fun PermissionInput.toDocument() = Permission(id = null, name = this.name, description = this.description,
    createdDate = null, createdByUser = null, lastModifiedDate = null, modifiedByUser = null, version = this.version)

fun Permission.toDto(): PermissionDto = PermissionDto(id = this.id, name = this.name, description = this.description, version = this.version,
    createdBy = this.createdByUser, createdDate = this.createdDate, modifiedBy = this.modifiedByUser, modifiedDate = this.lastModifiedDate)

fun User.toDto(): UserUnmappedPermissionsDto = UserUnmappedPermissionsDto(id = this.id!!, name = this.name,
    email = this.email, permissions = this.permissions, version = this.version,
    createdBy = this.createdByUser, createdDate = this.createdDate, modifiedBy = this.modifiedByUser, modifiedDate = this.lastModifiedDate)

fun User.toDto(permissions: Collection<Permission>?): UserDto = UserDto(id = this.id!!, name = this.name,
    email = this.email, permissions = permissions?.map { it.toDto() }, version = this.version,
    createdBy = this.createdByUser, createdDate = this.createdDate, modifiedBy = this.modifiedByUser, modifiedDate = this.lastModifiedDate)

fun getPageRequest(page: Int, size: Int, sort: String, direction: String) =
    Sort.by(Sort.Order(if (direction == "ASC") Sort.Direction.ASC else Sort.Direction.DESC, sort))
        .let { PageRequest.of(page, size, it) }