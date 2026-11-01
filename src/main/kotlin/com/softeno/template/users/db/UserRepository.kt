package com.softeno.template.users.db

import kotlinx.coroutines.flow.Flow
import org.springframework.data.domain.Pageable

import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface UserRepository : CoroutineCrudRepository<UserDocument, String> {
    fun findAllBy(pageable: Pageable): Flow<UserDocument>
    suspend fun findByIdAndVersion(id: String, version: Long): UserDocument?

    /** Users embedding a snapshot of the given permission (the fan-out target, ADR-0005). */
    fun findByPermissionsPermissionId(permissionId: String): Flow<UserDocument>
}
