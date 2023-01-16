package com.softeno.template

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document
data class Permission(
    @Id
    val id: String?,

    @Indexed(unique = true)
    val name: String,

    val description: String
)

@Document
data class User(
    @Id
    val id: String?,
    val name: String,
    val permissions: Set<String>
)

data class PermissionInput(
    val name: String,
    val description: String
)

data class UserInput(
    val name: String,
    val permissionIds: Set<String>
)

data class UserDto(
    val id: String,
    val name: String,
    val permissions: List<Permission>
)