package com.softeno.template.users.db

import org.springframework.data.annotation.*
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document
data class Permission(
    @Id
    val id: String?,

    @CreatedDate
    var createdDate: LocalDateTime?,

    @LastModifiedDate
    var lastModifiedDate: LocalDateTime?,

    @CreatedBy
    var createdByUser: String?,

    @LastModifiedBy
     var modifiedByUser: String?,

    @Version
    var version: Long?,

    @Indexed(unique = true)
    val name: String,

    val description: String
)

@Document
data class User(
    @Id
    val id: String?,

    @CreatedDate
    var createdDate: LocalDateTime?,

    @CreatedBy
    var createdByUser: String?,

    @LastModifiedBy
    var modifiedByUser: String?,

    @LastModifiedDate
    var lastModifiedDate: LocalDateTime?,

    @Version
    var version: Long?,

    val name: String,

    @Indexed(unique = true)
    val email: String,

    val permissions: Set<String>
)
