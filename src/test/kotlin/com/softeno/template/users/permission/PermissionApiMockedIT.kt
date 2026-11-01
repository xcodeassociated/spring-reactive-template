package com.softeno.template.users.permission

import com.ninjasquad.springmockk.MockkBean
import com.softeno.template.users.permission.db.PermissionCoroutineRepository
import com.softeno.template.testsupport.BaseIntegrationTest
import io.mockk.every
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Test

/**
 * REST test for `/coroutine/permissions` with the coroutine repository mocked —
 * demonstrates the MockK slice pattern without Testcontainers-backed persistence.
 */
class PermissionApiMockedIT : BaseIntegrationTest(), PermissionFixture {

    @MockkBean
    lateinit var permissionCoroutineRepositoryMock: PermissionCoroutineRepository

    @Test
    fun `should return mocked permissions`() {
        // given
        val aPermission = aPermission()

        every { permissionCoroutineRepositoryMock.findAllBy(any()) } returns flowOf(aPermission)

        // expect
        webTestClient.get().uri("/coroutine/permissions")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("[0]._id").isEqualTo(aPermission.id)
            .jsonPath("[0].name").isEqualTo(aPermission.name)
            .jsonPath("[0].description").isEqualTo(aPermission.description)
    }
}
