package com.softeno.template.config.graphql

import graphql.ErrorType
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.apache.commons.logging.LogFactory
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.stereotype.Component

/** Maps data-fetcher exceptions (incl. the domain BaseExceptions) to GraphQL errors. */
@Component
class GraphqlExceptionHandler : DataFetcherExceptionResolverAdapter() {
    private val log = LogFactory.getLog(javaClass)

    override fun resolveToSingleError(e: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        log.error("[graphql] error: ${e.message}")
        return e.toGraphQLError()
    }

    private fun Throwable.toGraphQLError(): GraphQLError? {
        log.warn("Exception while handling request: ${this.message}")
        return GraphqlErrorBuilder.newError().message(this.message).errorType(ErrorType.DataFetchingException).build()
    }
}
