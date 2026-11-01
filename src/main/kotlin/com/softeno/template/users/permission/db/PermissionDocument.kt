package com.softeno.template.users.permission.db

import com.softeno.template.common.BaseDocument
import org.springframework.data.annotation.*
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime
import kotlin.reflect.KClass

@Document
data class PermissionDocument(
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
