package com.softeno.template.users.api

import com.softeno.template.users.UserModifyCommand
import com.softeno.template.users.UserService
import org.springframework.graphql.data.method.annotation.*
import org.springframework.stereotype.Controller

@Controller
class UserGraphqlController(
    private val userService: UserService
) {
    @SchemaMapping(typeName = "Query", value = "getAllUsers")
    suspend fun getAllUsers(
        @Argument page: Int,
        @Argument size: Int,
        @Argument sort: String,
        @Argument direction: String,
    ) = userService.getAll(page, size, sort, direction)

    @QueryMapping
    suspend fun getUsersSize() = userService.size()

    @SchemaMapping(typeName = "Query", value = "getUser")
    suspend fun getUser(@Argument id: String) = userService.get(id)

    @MutationMapping
    suspend fun createUser(@Argument input: UserModifyCommand) = userService.create(input)

    @MutationMapping
    suspend fun updateUser(@Argument id: String, @Argument input: UserModifyCommand) = userService.update(id, input)

    @MutationMapping
    suspend fun deleteUser(@Argument id: String) = userService.delete(id).let { true }
}
