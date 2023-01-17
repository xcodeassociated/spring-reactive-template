package com.softeno.template.db

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document
data class Permission(
    @Id
    val id: String?,
    @Indexed(unique = true)
    val name: String,
    val description: String
)

@Document
data class User(
    @Id
    val id: String?,
    val name: String,
    @Indexed(unique = true)
    val email: String,
    val permissions: Set<String>
)
