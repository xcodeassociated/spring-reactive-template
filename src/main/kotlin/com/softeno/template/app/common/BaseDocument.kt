package com.softeno.template.app.common

import java.time.LocalDateTime
import kotlin.reflect.KClass

interface BaseDocument {
    val id: String?
    val version: Long?
    val createdBy: String?
    val createdDate: LocalDateTime?
    val modifiedBy: String?
    val modifiedDate: LocalDateTime?
    val clazz: KClass<out BaseDocument>?
}