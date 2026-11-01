package com.softeno.template.users.permission.db

import kotlinx.coroutines.flow.Flow
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PermissionRepository : CoroutineCrudRepository<PermissionDocument, String> {
    fun findAllBy(pageable: Pageable): Flow<PermissionDocument>
}
