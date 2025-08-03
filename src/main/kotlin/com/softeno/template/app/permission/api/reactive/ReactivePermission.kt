package com.softeno.template.app.permission.api.reactive

import com.softeno.template.app.common.getPageRequest
import com.softeno.template.app.permission.PermissionModifyCommand
import com.softeno.template.app.permission.api.PermissionDto
import com.softeno.template.app.permission.api.PermissionNotFoundException
import com.softeno.template.app.permission.api.toDto
import com.softeno.template.app.permission.db.PermissionDocument
import com.softeno.template.app.permission.db.PermissionsReactiveRepository
import com.softeno.template.app.permission.db.QPermissionDocument
import com.softeno.template.app.permission.toDocument
import com.softeno.template.app.permission.toDomain
import com.softeno.template.app.user.api.UserNotFoundException
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


@RestController
@RequestMapping("/reactive/")
@Validated
class ReactivePermissionController(
    private val permissionsReactiveRepository: PermissionsReactiveRepository,
    private val permissionsReactiveMongoTemplate: PermissionsReactiveMongoTemplate
) {
    @GetMapping("/permissions")
    fun getAllPermissions(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String
    ): Flux<PermissionDto> {
        return permissionsReactiveRepository.findAllBy(getPageRequest(page, size, sort, direction))
            .map { it.toDomain().toDto() }
    }

    @GetMapping("/permissions/{id}")
    fun getPermission(@PathVariable id: String): Mono<PermissionDto> {
        // note: ReactiveMongoRepository -> return permissionsReactiveRepository.findById(id)
        val permission = QPermissionDocument.permissionDocument
        val predicate = permission.id.eq(id)
        return permissionsReactiveRepository.findOne(predicate)
            .switchIfEmpty(Mono.error(UserNotFoundException("User not found with id: $id")))
            .map { it.toDomain().toDto() }
    }

    @DeleteMapping("/permissions/{id}")
    fun deletePermission(@PathVariable id: String): Mono<Void> = permissionsReactiveRepository.deleteById(id)

    @PostMapping("/permissions")
    fun createPermission(@RequestBody input: PermissionModifyCommand): Mono<PermissionDto> =
        permissionsReactiveRepository.save(input.toDocument()).map { it.toDomain().toDto() }

    @PutMapping("/permissions/{id}")
    fun updatePermission(
        @PathVariable id: String,
        @RequestBody(required = true) input: PermissionModifyCommand
    ): Mono<PermissionDto> =
        permissionsReactiveMongoTemplate.findAndModify(id, input)
            .switchIfEmpty(Mono.error(PermissionNotFoundException("Permission: $id Not Found")))
            .map { it.toDomain().toDto() }
}

@Component
class PermissionsReactiveMongoTemplate(val mongoTemplate: ReactiveMongoTemplate) {
    fun findAndModify(id: String, input: PermissionModifyCommand): Mono<PermissionDocument> {
        val query = Query(Criteria.where("id").`is`(id).and("version").`is`(input.version))
        val update = Update().set("name", input.name).set("description", input.description)
        // note: Update() has more `set` operations based on the document element type to be modified
        val options = FindAndModifyOptions.options().returnNew(true)
        // note: can be changed to `upsert`
        // ref: https://medium.com/geekculture/types-of-update-operations-in-mongodb-using-spring-boot-11d5d4ce88cf
        return mongoTemplate.findAndModify(query, update, options, PermissionDocument::class.java)
    }
}
