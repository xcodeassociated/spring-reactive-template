package com.softeno.template.users.http.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.softeno.template.users.db.Permission
import com.softeno.template.users.db.User
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

data class PermissionInput(
    val name: String,
    val description: String
)

data class PermissionDto(
    @JsonProperty("_id")
    val id: String?,
    val name: String,
    val description: String
)
data class UserInput(
    val name: String,
    val email: String,
    @JsonProperty("role")
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val permissionIds: Set<String>
)

data class UserDto(
    @JsonProperty("_id")
    val id: String,
    val name: String,
    val email: String,
    @JsonProperty("role")
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val permissions: List<PermissionDto>
)

fun PermissionInput.toDocument() = Permission(id = null, name = this.name, description = this.description)

fun Permission.toDto(): PermissionDto = PermissionDto(id = this.id, name = this.name, description = this.description)

fun User.toDto(permissions: Collection<Permission>): UserDto = UserDto(id = this.id!!, name = this.name, email = this.email, permissions = permissions.map { it.toDto() })

fun getPageRequest(page: Int, size: Int, sort: String, direction: String) =
    Sort.by(Sort.Order(if (direction == "ASC") Sort.Direction.ASC else Sort.Direction.DESC, sort))
        .let { PageRequest.of(page, size, it) }