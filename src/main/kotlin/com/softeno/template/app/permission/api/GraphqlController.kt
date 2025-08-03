package com.softeno.template.app.permission.api

import com.softeno.template.app.permission.PermissionModifyCommand
import com.softeno.template.app.permission.PermissionService
import graphql.ErrorType
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.apache.commons.logging.LogFactory
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller


@Controller
class GraphqlPermissionController(
    val permissionService: PermissionService
) {

    @QueryMapping
    suspend fun getAllPermissions(
        @Argument page: Int,
        @Argument size: Int,
        @Argument sort: String,
        @Argument direction: String
    ) =
        permissionService.getAll(page, size, sort, direction)

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


@Component
class GraphQLExceptionHandler : DataFetcherExceptionResolverAdapter() {
    private val log = LogFactory.getLog(javaClass)

    override fun resolveToSingleError(e: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        log.error("[graphql] error: ${e.message}")
        return e.toGraphQLError()
    }

    private fun Throwable.toGraphQLError(): GraphQLError? {
        log.warn("Exception while handling request: ${this.message}", this)
        return GraphqlErrorBuilder.newError().message(this.message).errorType(ErrorType.DataFetchingException).build()
    }
}