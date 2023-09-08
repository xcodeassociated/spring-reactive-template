package com.softeno.template.users.http.coroutine

import com.softeno.template.users.db.Permission
import com.softeno.template.users.db.User
import kotlinx.coroutines.flow.Flow
import org.springframework.data.domain.Pageable
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PermissionCoroutineRepository :
    CoroutineCrudRepository<Permission, String>, ReactiveQuerydslPredicateExecutor<Permission> {
    fun findAllBy(pageable: Pageable): Flow<Permission>
}

@Repository
interface UserCoroutineRepository : CoroutineCrudRepository<User, String> {
    fun findAllBy(pageable: Pageable): Flow<User>
    suspend fun findByIdAndVersion(id: String, version: Long): User?
}