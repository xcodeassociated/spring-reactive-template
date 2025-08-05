package com.softeno.template.app.permission.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.softeno.template.app.permission.Permission
import com.softeno.template.app.permission.PermissionModifyCommand
import com.softeno.template.app.permission.PermissionService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime


@RestController
@RequestMapping("/coroutine/")
@Validated
class CoroutinePermissionController(
    val permissionService: PermissionService
) {
    //    @PreAuthorize(value = "hasRole('admin')")
    @GetMapping("/permissions")
    suspend fun getAllPermissions(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String,
    ): Flow<PermissionDto> = permissionService.getAll(page, size, sort, direction).map { it.toDto() }

    @GetMapping("/permissions/{id}")
    suspend fun getPermission(@PathVariable id: String): PermissionDto? =
        permissionService.get(id).toDto()

    @DeleteMapping("/permissions/{id}")
    suspend fun deletePermission(@PathVariable id: String) =
        permissionService.delete(id)

    @PostMapping("/permissions")
    suspend fun createPermission(@RequestBody(required = true) input: PermissionModifyCommand): PermissionDto =
        permissionService.create(input).toDto()

    @PutMapping("/permissions/{id}")
    suspend fun updatePermission(
        @PathVariable id: String,
        @RequestBody(required = true) input: PermissionModifyCommand
    ): PermissionDto =
        permissionService.update(id, input).toDto()
}

data class PermissionDto(
    @param:JsonProperty("_id")
    val id: String?,
    val name: String,
    val description: String,
    val version: Long?,
    val createdBy: String?,
    val createdDate: LocalDateTime?,
    val modifiedBy: String?,
    val modifiedDate: LocalDateTime?
)

fun Permission.toDto(): PermissionDto = PermissionDto(
    id = this.id,
    name = this.name,
    description = this.description,
    version = this.base?.version,
    createdBy = this.base?.createdBy,
    createdDate = this.base?.createdDate,
    modifiedBy = this.base?.modifiedBy,
    modifiedDate = this.base?.modifiedDate
)