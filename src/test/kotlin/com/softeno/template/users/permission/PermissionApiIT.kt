package com.softeno.template.users.permission

import com.softeno.template.testsupport.BaseIntegrationTest
import com.softeno.template.testsupport.R
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

/**
 * REST tests for the coroutine permission endpoints (`/coroutine/permissions`)
 * against the real Mongo-backed service.
 */
class PermissionApiIT : BaseIntegrationTest(), PermissionFixture {

    @Test
    fun `should return empty permissions`() {
        webTestClient.get().uri("/coroutine/permissions")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("[]")
    }

    @Test
    fun `should return saved permissions`() {
        // given
        val permission = runBlocking {
            permissionRepository.save(aPermissionToSave())
        }

        // expect
        webTestClient.get().uri("/coroutine/permissions")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("[0]._id").isEqualTo(permission.id)
            .jsonPath("[0].name").isEqualTo(permission.name)
            .jsonPath("[0].description").isEqualTo(permission.description)
    }

    @Test
    fun `should create permission via rest`() {
        webTestClient.post().uri("/coroutine/permissions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"${R.name()}","description":"${R.text()}"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("_id").exists()
            .jsonPath("version").isEqualTo(0)
    }

    @Test
    fun `should update permission via rest`() {
        val permission = runBlocking { permissionRepository.save(aPermissionToSave()) }

        webTestClient.put().uri("/coroutine/permissions/${permission.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"${R.name()}","description":"updated","version":${permission.version}}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("_id").isEqualTo(permission.id)
            .jsonPath("description").isEqualTo("updated")
            .jsonPath("version").isEqualTo(1)
    }
}
