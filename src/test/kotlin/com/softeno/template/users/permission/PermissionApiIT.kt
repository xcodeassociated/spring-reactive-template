package com.softeno.template.users.permission

import com.softeno.template.testsupport.BaseIntegrationTest
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

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
            permissionsReactiveRepository.save(aPermissionToSave()).awaitSingle()
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
}
