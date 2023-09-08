package com.softeno.template.users.http.coroutine

import com.softeno.template.users.http.dto.PermissionInput
import com.softeno.template.users.http.dto.UserInput
import graphql.ErrorType
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.apache.commons.logging.LogFactory
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono
import java.security.Principal

@Controller
class GraphqlPermissionController(
    val permissionService: CoroutinePermissionService
) {

    @QueryMapping
    suspend fun getAllPermissions(@Argument page: Int, @Argument size: Int, @Argument sort: String, @Argument direction: String) =
        permissionService.getAll(page, size, sort, direction)

    @QueryMapping
    suspend fun getPermission(@Argument id: String) = permissionService.get(id)

    @MutationMapping
    suspend fun createPermission(@Argument input: PermissionInput) = permissionService.create(input)

    @MutationMapping
    suspend fun updatePermission(@Argument id: String, @Argument input: PermissionInput) = permissionService.update(id, input)

    @MutationMapping
    suspend fun deletePermission(@Argument id: String): Boolean = permissionService.delete(id).let { true }
}

@Controller
class GraphqlUserController (
    val userService: CoroutineUserService
) {
    @SchemaMapping(typeName = "Query", value = "getAllUsers")
    suspend fun getAllUsers(@Argument page: Int, @Argument size: Int, @Argument sort: String, @Argument direction: String, principal: Principal) =
        userService.getAll(page, size, sort, direction, Mono.just(principal))

    @QueryMapping
    suspend fun getUsersSize() = userService.size()

    @QueryMapping
    suspend fun getUser(@Argument id: String) = userService.get(id)

    @MutationMapping
    suspend fun createUser(@Argument input: UserInput) = userService.create(input)

    @MutationMapping
    suspend fun updateUser(@Argument id: String, @Argument input: UserInput) = userService.update(id, input)

    @MutationMapping
    suspend fun deleteUser(@Argument id: String) = userService.delete(id).let { true }

}


@Component
class GraphQLExceptionHandler : DataFetcherExceptionResolverAdapter() {
    private val log = LogFactory.getLog(javaClass)

    override fun resolveToSingleError(e: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        log.error("[graphql] error: ${e.message}")
        return super.resolveToSingleError(e, env)
//        return when (e) {
//            is NotFoundException -> toGraphQLError(e)
//            else -> super.resolveToSingleError(e, env)
//        }
    }
    private fun toGraphQLError(e: Throwable): GraphQLError? {
        log.warn("Exception while handling request: ${e.message}", e)
        return GraphqlErrorBuilder.newError().message(e.message).errorType(ErrorType.DataFetchingException).build()
    }
}