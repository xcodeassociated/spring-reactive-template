package com.softeno.template.app.permission

import com.softeno.template.app.common.BaseDocument

data class Permission(
    val id: String?,
    val base: BaseDocument?,
    val name: String,
    val description: String,
)

data class PermissionModifyCommand(
    val name: String,
    val description: String,
    val version: Long?
)