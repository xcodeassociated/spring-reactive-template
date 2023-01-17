package com.softeno.template

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document
data class Permission(
    @Id
    @JsonProperty("_id")
    val id: String?,
    @Indexed(unique = true)
    val name: String,
    val description: String
)

@Document
data class User(
    @Id
    @JsonProperty("_id")
    val id: String?,
    val name: String,
    @Indexed(unique = true)
    val email: String,
    @JsonProperty("role")
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val permissions: Set<String>
)

data class PermissionInput(
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
    val permissions: List<Permission>
)