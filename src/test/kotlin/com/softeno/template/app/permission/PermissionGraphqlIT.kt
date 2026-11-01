package com.softeno.template.app.permission

import com.softeno.template.testsupport.BaseIntegrationTest
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.graphql.test.tester.GraphQlTester

/**
 * GraphQL tests for the permission queries (`app/permission/api/GraphqlController`)
 * against the real Mongo-backed service.
 */
class PermissionGraphqlIT : BaseIntegrationTest(), PermissionFixture {

    @Autowired
    private lateinit var graphQlTester: GraphQlTester

    @Test
    fun `should get permissions`() {
        // given
        val aPermission = runBlocking {
            permissionsReactiveRepository.save(aPermissionToSave()).awaitSingle()
        }

        val query = """
            query {
              getAllPermissions(page: 0, size:10, sort:"id", direction:"ASC") {
                name,
                description
              }
            }
        """.trimIndent()

        // note: a returned result differs from graphql because we use: .path("getAllPermissions")
        val expected = """
            [{"name":"${aPermission.name}","description":"${aPermission.description}"}]
        """.trimIndent()

        // expect
        graphQlTester.document(query)
            .execute()
            .path("getAllPermissions")
            .matchesJson(expected)
    }
}
