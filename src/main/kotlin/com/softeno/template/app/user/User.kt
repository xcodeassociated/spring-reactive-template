package com.softeno.template.app.user

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.softeno.template.app.common.BaseDocument
import com.softeno.template.app.permission.Permission


data class User(
    val id: String?,
    val base: BaseDocument?,
    val name: String,
    val email: String,
    val permissions: List<Permission>?,
) {
    constructor(command: UserModifyCommand) : this(command, null)
    constructor(command: UserModifyCommand, permissions: List<Permission>?)
            : this(id = null, name = command.name, email = command.email, permissions = permissions, base = null)
}

data class UserModifyCommand(
    val name: String,
    val email: String,
    @JsonProperty("role")
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val permissionIds: Set<String>,
    val version: Long?
)