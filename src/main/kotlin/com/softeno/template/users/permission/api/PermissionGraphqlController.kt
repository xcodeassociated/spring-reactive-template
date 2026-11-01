package com.softeno.template.users.permission.api

import com.softeno.template.users.permission.PermissionModifyCommand
import com.softeno.template.users.permission.PermissionService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

@Controller
class PermissionGraphqlController(
    private val permissionService: PermissionService
) {
    @QueryMapping
    suspend fun getAllPermissions(
        @Argument page: Int,
        @Argument size: Int,
        @Argument sort: String,
        @Argument direction: String
    ) = permissionService.getAll(page, size, sort, direction)

    @QueryMapping
    suspend fun getPermission(@Argument id: String) = permissionService.get(id)

    @MutationMapping
    suspend fun createPermission(@Argument input: PermissionModifyCommand) = permissionService.create(input)

    @MutationMapping
    suspend fun updatePermission(@Argument id: String, @Argument input: PermissionModifyCommand) =
        permissionService.update(id, input)

    @MutationMapping
    suspend fun deletePermission(@Argument id: String): Boolean = permissionService.delete(id).let { true }
}
