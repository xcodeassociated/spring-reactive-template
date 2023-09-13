package com.softeno.template.app.user.api

import com.softeno.template.app.permission.db.PermissionDocument
import com.softeno.template.app.user.UserModifyCommand
import com.softeno.template.app.user.db.UserDocument
import com.softeno.template.app.user.service.UserDocumentService
import com.softeno.template.app.user.service.UserService
import org.springframework.graphql.data.method.annotation.*
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono
import java.security.Principal

@Controller
class GraphqlUserController(
    private val userDocumentService: UserDocumentService,
    private val userService: UserService
) {
    @SchemaMapping(typeName = "Query", value = "getAllUsers")
    suspend fun getAllUsers(
        @Argument page: Int,
        @Argument size: Int,
        @Argument sort: String,
        @Argument direction: String,
        principal: Principal
    ) = userDocumentService.getUsersWithoutPermissions(page, size, sort, direction, Mono.just(principal))

    @QueryMapping
    suspend fun getUsersSize() = userService.size()

    @SchemaMapping(typeName = "Query", value = "getUser")
    suspend fun getUser(@Argument id: String) = userDocumentService.get(id)

//    @SchemaMapping(typeName="User", field="permissions")
//    suspend fun getUserPermissions(user: User) = userService.getUserPermissions(user)

    @BatchMapping(typeName = "User", field = "permissions")
    suspend fun getUsersWithPermissions(userDocuments: List<UserDocument>): Map<UserDocument, List<PermissionDocument>> {
        return userDocumentService.getUserAndPermissions(userDocuments)
    }

    @MutationMapping
    suspend fun createUser(@Argument input: UserModifyCommand) = userService.create(input)

    @MutationMapping
    suspend fun updateUser(@Argument id: String, @Argument input: UserModifyCommand) = userService.update(id, input)

    @MutationMapping
    suspend fun deleteUser(@Argument id: String) = userService.delete(id).let { true }

}
