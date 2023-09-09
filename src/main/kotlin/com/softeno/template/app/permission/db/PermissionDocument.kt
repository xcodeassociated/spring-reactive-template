package com.softeno.template.app.permission.db

import com.softeno.template.app.common.BaseDocument
import kotlinx.coroutines.flow.Flow
import org.springframework.data.annotation.*
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.time.LocalDateTime
import kotlin.reflect.KClass

@Document
data class PermissionDocument(
    @Id
    override val id: String?,

    @CreatedDate
    override var createdDate: LocalDateTime?,

    @LastModifiedDate
    var lastModifiedDate: LocalDateTime?,

    @CreatedBy
    var createdByUser: String?,

    @LastModifiedBy
    var modifiedByUser: String?,

    @Version
    override var version: Long?,

    @Indexed(unique = true)
    val name: String,

    val description: String
) : BaseDocument {
    @get:Transient
    override val createdBy: String?
        get() = createdByUser

    @get:Transient
    override val modifiedBy: String?
        get() = modifiedByUser

    @get:Transient
    override val modifiedDate: LocalDateTime?
        get() = lastModifiedDate

    @get:Transient
    override val clazz: KClass<out PermissionDocument>
        get() = this::class
}

@Repository
interface PermissionCoroutineRepository :
    CoroutineCrudRepository<PermissionDocument, String>, ReactiveQuerydslPredicateExecutor<PermissionDocument> {
    fun findAllBy(pageable: Pageable): Flow<PermissionDocument>
}

@Repository
interface PermissionsReactiveRepository : ReactiveMongoRepository<PermissionDocument, String>,
    ReactiveQuerydslPredicateExecutor<PermissionDocument> {
    fun findAllBy(pageable: Pageable): Flux<PermissionDocument>
    fun findByIdIn(ids: Set<String>): Flux<PermissionDocument>
}
