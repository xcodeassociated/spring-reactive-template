package com.softeno.template.app.user.db

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
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import kotlin.reflect.KClass

@Document
data class UserDocument(
    @Id
    override val id: String?,

    @CreatedDate
    override var createdDate: LocalDateTime?,

    @CreatedBy
    var createdByUser: String?,

    @LastModifiedBy
    var modifiedByUser: String?,

    @LastModifiedDate
    var lastModifiedDate: LocalDateTime?,

    @Version
    override var version: Long?,

    val name: String,

    @Indexed(unique = true)
    val email: String,

    val permissions: Set<String>
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
    override val clazz: KClass<out UserDocument>
        get() = this::class
}

@Repository
interface UserCoroutineRepository : CoroutineCrudRepository<UserDocument, String> {
    fun findAllBy(pageable: Pageable): Flow<UserDocument>
    suspend fun findByIdAndVersion(id: String, version: Long): UserDocument?
}

@Repository
interface UserReactiveRepository : ReactiveMongoRepository<UserDocument, String>,
    ReactiveQuerydslPredicateExecutor<UserDocument> {
    fun findAllBy(pageable: Pageable): Flux<UserDocument>
    fun findByIdAndVersion(id: String, version: Long): Mono<UserDocument>
}