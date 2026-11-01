package com.softeno.template.common

import com.softeno.template.users.permission.api.PermissionNotFoundException
import com.softeno.template.users.api.UserNotFoundException

object ErrorFactory {
    fun errorPermissionNotFound(id: String): PermissionNotFoundException {
        val message = "Permission not found with id: $id"
        return PermissionNotFoundException(message)
    }

    fun errorUserNotFound(id: String): UserNotFoundException {
        val message = "User not found with id: $id"
        return UserNotFoundException(message)
    }
}