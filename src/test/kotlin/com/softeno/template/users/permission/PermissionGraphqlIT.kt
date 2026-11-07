package com.softeno.template.users.permission

import com.softeno.template.testsupport.BaseIntegrationTest
import com.softeno.template.testsupport.R
import com.softeno.template.users.db.PermissionSnapshot
import com.softeno.template.users.db.UserDocument
import com.softeno.template.users.permission.db.PermissionDocument
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.graphql.test.tester.GraphQlTester

/**
 * GraphQL tests for the permission queries and mutations
 * (`users/permission/api/GraphqlController`) against the real Mongo-backed service.
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
    fun `should get permissions size`() {
        // given
        runBlocking {
            permissionRepository.save(aPermissionToSave())
            permissionRepository.save(aPermissionToSave())
        }

        // expect: the count mirrors /coroutine/permissionsCount for pagination
        graphQlTester.document("query { getPermissionsSize }")
            .execute()
            .path("getPermissionsSize").entity(Int::class.javaObjectType).isEqualTo(2)
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

    @Test
    fun `should create permission via graphql`() {
        // given
        val name = R.name()
        val description = R.text()

        val mutation = """
            mutation {
              createPermission(input: {name: "$name", description: "$description"}) {
                id,
                name,
                description,
                version
              }
            }
        """.trimIndent()

        // expect
        val result = graphQlTester.document(mutation).execute()
        result.path("createPermission.name").entity(String::class.java).isEqualTo(name)
        result.path("createPermission.description").entity(String::class.java).isEqualTo(description)
        result.path("createPermission.version").entity(Int::class.javaObjectType).isEqualTo(0)
        result.path("createPermission.id").hasValue()
    }

    @Test
    fun `create permission accepts null description - schema field is nullable`() {
        // regression: PermissionModifyCommand used a non-null String, so the
        // schema-legal description:null blew up with a constructor exception
        val name = R.name()

        val mutation = """
            mutation {
              createPermission(input: {name: "$name", description: null}) {
                id,
                name,
                description
              }
            }
        """.trimIndent()

        val result = graphQlTester.document(mutation).execute()
        result.path("createPermission.name").entity(String::class.java).isEqualTo(name)
        result.path("createPermission.description").entity(String::class.java).isEqualTo("")
        result.path("createPermission.id").hasValue()
    }

    @Test
    fun `update permission accepts null description`() {
        val permission = runBlocking { permissionRepository.save(aPermissionToSave()) }
        val name = R.name()

        val mutation = """
            mutation {
              updatePermission(id: "${permission.id}", input: {name: "$name", description: null, version: ${permission.version}}) {
                id,
                name,
                description
              }
            }
        """.trimIndent()

        val result = graphQlTester.document(mutation).execute()
        result.path("updatePermission.name").entity(String::class.java).isEqualTo(name)
        result.path("updatePermission.description").entity(String::class.java).isEqualTo("")
    }

    @Test
    fun `should update permission via graphql`() {
        // given
        val permission = runBlocking { permissionRepository.save(aPermissionToSave()) }
        val name = R.name()

        val mutation = """
            mutation {
              updatePermission(id: "${permission.id}", input: {name: "$name", description: "updated", version: ${permission.version}}) {
                id,
                name,
                description,
                version
              }
            }
        """.trimIndent()

        // expect: optimistic-locking findAndModify returns the new document, version bumped
        val result = graphQlTester.document(mutation).execute()
        result.path("updatePermission.id").entity(String::class.java).isEqualTo(permission.id!!)
        result.path("updatePermission.name").entity(String::class.java).isEqualTo(name)
        result.path("updatePermission.description").entity(String::class.java).isEqualTo("updated")
        result.path("updatePermission.version").entity(Int::class.javaObjectType).isEqualTo(1)
    }

    @Test
    fun `update with a stale version returns a graphql error`() {
        // given: a stored permission and a version that cannot match it
        val permission = runBlocking { permissionRepository.save(aPermissionToSave()) }

        // expect: findAndModify matches nothing -> mapped PermissionNotFoundException
        graphQlTester.document(
            """mutation { updatePermission(id: "${permission.id}", input: {name: "${R.name()}", description: "${R.text()}", version: ${R.int(1000, 9999)}}) { id } }"""
        )
            .execute()
            .errors()
            .satisfy { errors ->
                assertTrue(errors.any { it.message?.contains("Permission not found") == true }) {
                    "expected the mapped PermissionNotFoundException, got: $errors"
                }
            }
    }

    @Test
    fun `should delete permission via graphql`() {
        // given
        val permission = runBlocking { permissionRepository.save(aPermissionToSave()) }

        // expect
        graphQlTester.document("""mutation { deletePermission(id: "${permission.id}") }""")
            .execute()
            .path("deletePermission").entity(Boolean::class.javaObjectType).isEqualTo(true)

        assertTrue(runBlocking { permissionRepository.findById(permission.id!!) } == null) {
            "expected the permission to be gone after deletePermission"
        }
    }

    @Test
    fun `permission update propagates into every user holding it`() {
        // given: a user embedding a snapshot of the permission (ADR-0005)
        val permission = runBlocking { permissionRepository.save(aPermissionToSave()) }
        val user = runBlocking { userRepository.save(aUserWith(permission)) }
        val name = R.name()

        // when
        graphQlTester.document(
            """mutation { updatePermission(id: "${permission.id}", input: {name: "$name", description: "updated", version: ${permission.version}}) { id } }"""
        ).execute().path("updatePermission").hasValue()

        // expect: the UserPermissionSync fan-out replaced the snapshot in the user
        graphQlTester.document("""query { getUser(id: "${user.id}") { permissions { id, name, description, version } } }""")
            .execute()
            .path("getUser.permissions[0].id").entity(String::class.java).isEqualTo(permission.id!!)
            .path("getUser.permissions[0].name").entity(String::class.java).isEqualTo(name)
            .path("getUser.permissions[0].description").entity(String::class.java).isEqualTo("updated")
    }

    @Test
    fun `permission delete is pulled from every user holding it`() {
        // given
        val permission = runBlocking { permissionRepository.save(aPermissionToSave()) }
        val user = runBlocking { userRepository.save(aUserWith(permission)) }

        // when
        graphQlTester.document("""mutation { deletePermission(id: "${permission.id}") }""")
            .execute()
            .path("deletePermission").entity(Boolean::class.javaObjectType).isEqualTo(true)

        // expect: no dangling snapshot left on the user
        graphQlTester.document("""query { getUser(id: "${user.id}") { permissions { id } } }""")
            .execute()
            .path("getUser.permissions").matchesJson("[]")
    }

    @Test
    fun `create user via graphql assigns permission snapshots`() {
        // given
        val permission = runBlocking { permissionRepository.save(aPermissionToSave()) }

        // when: the schema's UserInput uses `permissionIds` (wire name for `role`)
        val response = graphQlTester.document(
            """mutation { createUser(input: {name: "${R.name()}", email: "${R.email()}", permissionIds: ["${permission.id}"]}) { id, permissions { id, name } } }"""
        ).execute()
        response.errors().verify()

        // expect: the snapshot was embedded at write time
        response.path("createUser.permissions[0].id").entity(String::class.java).isEqualTo(permission.id!!)
        response.path("createUser.permissions[0].name").entity(String::class.java).isEqualTo(permission.name)
    }

    private fun aUserWith(vararg permissions: PermissionDocument) = UserDocument(
        id = null,
        createdDate = null,
        createdByUser = null,
        modifiedByUser = null,
        lastModifiedDate = null,
        version = null,
        name = R.name(),
        email = R.email(),
        permissions = permissions.map { PermissionSnapshot.of(it) }.toSet()
    )
}
