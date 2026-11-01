package com.softeno.template.users.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.softeno.template.testsupport.BaseIntegrationTest
import com.softeno.template.testsupport.R
import com.softeno.template.users.permission.PermissionFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

/**
 * REST tests for the users core (`/coroutine/users*`): CRUD round-trips on the
 * real Mongo-backed service, permission mapping through the `role` field,
 * optimistic-locking and not-found guards (the generic handler answers 500),
 * and the `createdBy` audit trail from the authenticated test principal.
 */
class UserApiIT : BaseIntegrationTest(), PermissionFixture {

    private val mapper = ObjectMapper()

    private fun createJson(name: String = R.name(), email: String = R.email(), role: List<String> = emptyList()) =
        """{"name":"$name","email":"$email","role":[${role.joinToString(",") { "\"$it\"" }}]}"""

    private fun post(body: String, sub: String = R.sub()) =
        webTestClient.post().uri("/coroutine/users")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Test-Sub", sub)
            .bodyValue(body)
            .exchange()

    private fun getJson(uri: String) =
        webTestClient.get().uri(uri).exchange()
            .expectStatus().isOk()
            .expectBody(String::class.java).returnResult().responseBody!!

    @Test
    fun `create persists document with audit and empty role`() {
        val sub = R.sub("creator")
        val body = createJson()

        post(body, sub)
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("_id").exists()
            .jsonPath("name").isEqualTo(mapper.readTree(body).get("name").asText())
            .jsonPath("role").isEqualTo(emptyList<String>())
            .jsonPath("version").isEqualTo(0)
            .jsonPath("createdBy").isEqualTo(sub)
            .jsonPath("createdDate").exists()
    }

    @Test
    fun `create with permissions maps role ids to permission dtos`() {
        val permission = runBlocking { permissionRepository.save(aPermissionToSave()) }

        post(createJson(role = listOf(permission.id!!)))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("role[0]._id").isEqualTo(permission.id)
            .jsonPath("role[0].name").isEqualTo(permission.name)
    }

    @Test
    fun `create with unknown permission id fails`() {
        post(createJson(role = listOf(R.objectId())))
            .expectStatus().is5xxServerError  // PermissionNotFoundException -> generic handler
            .expectBody().jsonPath("errorCode").isEqualTo("E000")
    }

    @Test
    fun `get returns user with mapped permissions`() {
        val permission = runBlocking { permissionRepository.save(aPermissionToSave()) }
        val created = mapper.readTree(
            post(createJson(role = listOf(permission.id!!)))
                .expectStatus().isOk()
                .expectBody(String::class.java).returnResult().responseBody
        )
        val id = created.get("_id").asText()

        webTestClient.get().uri("/coroutine/users/$id")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("_id").isEqualTo(id)
            .jsonPath("role[0]._id").isEqualTo(permission.id)
    }

    @Test
    fun `get unknown user fails`() {
        webTestClient.get().uri("/coroutine/users/${R.objectId()}")
            .exchange()
            .expectStatus().is5xxServerError  // UserNotFoundException -> generic handler
            .expectBody().jsonPath("errorCode").isEqualTo("E000")
    }

    @Test
    fun `list and count reflect created users`() {
        val before: Int = mapper.readTree(getJson("/coroutine/users?size=1000")).size()
        val countBefore = getJson("/coroutine/usersCount").toLong()

        post(createJson()).expectStatus().isOk()
        post(createJson()).expectStatus().isOk()

        assertEquals(countBefore + 2, getJson("/coroutine/usersCount").toLong())
        assertEquals(before + 2, mapper.readTree(getJson("/coroutine/users?size=1000")).size())
    }

    @Test
    fun `update with correct version bumps version`() {
        val created = mapper.readTree(
            post(createJson()).expectStatus().isOk()
                .expectBody(String::class.java).returnResult().responseBody
        )
        val id = created.get("_id").asText()
        val name = R.name()

        webTestClient.put().uri("/coroutine/users/$id")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"$name","email":"${R.email()}","role":[],"version":${created.get("version").asLong()}}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("_id").isEqualTo(id)
            .jsonPath("name").isEqualTo(name)
            .jsonPath("version").isEqualTo(1)
    }

    @Test
    fun `update with stale version fails`() {
        val created = mapper.readTree(
            post(createJson()).expectStatus().isOk()
                .expectBody(String::class.java).returnResult().responseBody
        )
        val id = created.get("_id").asText()

        webTestClient.put().uri("/coroutine/users/$id")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"${R.name()}","email":"${R.email()}","role":[],"version":99}""")
            .exchange()
            .expectStatus().is5xxServerError  // version guard -> UserNotFoundException
    }

    @Test
    fun `update without version fails`() {
        val created = mapper.readTree(
            post(createJson()).expectStatus().isOk()
                .expectBody(String::class.java).returnResult().responseBody
        )

        webTestClient.put().uri("/coroutine/users/${created.get("_id").asText()}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"${R.name()}","email":"${R.email()}","role":[]}""")
            .exchange()
            .expectStatus().is5xxServerError  // VersionMissingException
    }

    @Test
    fun `permission rename propagates into the user's embedded role`() {
        val permission = runBlocking { permissionRepository.save(aPermissionToSave()) }
        val created = mapper.readTree(
            post(createJson(role = listOf(permission.id!!))).expectStatus().isOk()
                .expectBody(String::class.java).returnResult().responseBody
        )

        val newName = R.name()
        webTestClient.put().uri("/coroutine/permissions/${permission.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"$newName","description":"renamed","version":${permission.version}}""")
            .exchange()
            .expectStatus().isOk()

        webTestClient.get().uri("/coroutine/users/${created.get("_id").asText()}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("role[0]._id").isEqualTo(permission.id)
            .jsonPath("role[0].name").isEqualTo(newName)
            .jsonPath("role[0].description").isEqualTo("renamed")
    }

    @Test
    fun `permission delete is pulled from the user's embedded role`() {
        val permission = runBlocking { permissionRepository.save(aPermissionToSave()) }
        val created = mapper.readTree(
            post(createJson(role = listOf(permission.id!!))).expectStatus().isOk()
                .expectBody(String::class.java).returnResult().responseBody
        )

        webTestClient.delete().uri("/coroutine/permissions/${permission.id}")
            .exchange()
            .expectStatus().isOk()

        webTestClient.get().uri("/coroutine/users/${created.get("_id").asText()}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("role").isEqualTo(emptyList<String>())
    }

    @Test
    fun `delete removes the user`() {
        val id = mapper.readTree(
            post(createJson()).expectStatus().isOk()
                .expectBody(String::class.java).returnResult().responseBody
        ).get("_id").asText()

        webTestClient.delete().uri("/coroutine/users/$id")
            .exchange()
            .expectStatus().isOk()

        webTestClient.get().uri("/coroutine/users/$id")
            .exchange()
            .expectStatus().is5xxServerError
    }
}
