package com.softeno.template.users.permission

/** Create/update input for permissions (works directly on PermissionDocument). */
data class PermissionModifyCommand(
    val name: String,
    val description: String,
    val version: Long?
)
