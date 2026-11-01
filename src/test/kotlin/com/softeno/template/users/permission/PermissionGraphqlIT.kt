package com.softeno.template.users.permission

import com.softeno.template.testsupport.BaseIntegrationTest
import com.softeno.template.testsupport.R
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.graphql.test.tester.GraphQlTester

/**
 * GraphQL tests for the permission queries (`users/permission/api/GraphqlController`)
 * against the real Mongo-backed service.
 */
class PermissionGraphqlIT : BaseIntegrationTest(), PermissionFixture {

    @Autowired
    private lateinit var graphQlTester: GraphQlTester

    @Test
    fun `should get permissions`() {
        // given
        val aPermission = runBlocking {
            permissionRepository.save(aPermissionToSave())
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

    @Test
    fun `query of unknown permission returns a graphql error`() {
        // covers the global GraphqlExceptionHandler mapping of BaseExceptions
        graphQlTester.document("""{ getPermission(id: "${R.objectId()}") { name } }""")
            .execute()
            .errors()
            .satisfy { errors ->
                assertTrue(errors.any { it.message?.contains("Permission not found") == true }) {
                    "expected the mapped PermissionNotFoundException, got: $errors"
                }
            }
    }
}
