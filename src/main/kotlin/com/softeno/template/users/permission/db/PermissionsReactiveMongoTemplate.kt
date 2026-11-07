package com.softeno.template.users.permission.db

import com.softeno.template.users.permission.PermissionModifyCommand
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Optimistic-locking findAndModify helper over the reactive Mongo template.
 * Infrastructure (not a feature flavor) — used by the coroutine PermissionService.
 */
@Component
class PermissionsReactiveMongoTemplate(val mongoTemplate: ReactiveMongoTemplate) {
    fun findAndModify(id: String, input: PermissionModifyCommand): Mono<PermissionDocument> {
        val query = Query(Criteria.where("id").`is`(id).and("version").`is`(input.version))
        // null description -> "" (the document field is non-nullable; never
        // write null into it) — matches PermissionInput's nullable schema field
        val update = Update().set("name", input.name).set("description", input.description ?: "")
        // note: Update() has more `set` operations based on the document element type to be modified
        val options = FindAndModifyOptions.options().returnNew(true)
        // note: can be changed to `upsert`
        // ref: https://medium.com/geekculture/types-of-update-operations-in-mongodb-using-spring-boot-11d5d4ce88cf
        return mongoTemplate.findAndModify(query, update, options, PermissionDocument::class.java)
    }
}
