package com.softeno.template.reactive

import com.softeno.template.*
import com.softeno.template.coroutine.dto.*
import com.softeno.template.db.Permission
import com.softeno.template.db.QPermission
import com.softeno.template.db.User
import com.softeno.template.event.AppEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.server.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono


@RestController
@RequestMapping("/reactive/")
@Validated
class ReactivePermissionController(
    val permissionsReactiveRepository: PermissionsReactiveRepository,
    val permissionsReactiveMongoTemplate: PermissionsReactiveMongoTemplate
) {
    @GetMapping("/permissions")
    fun getAllPermissions(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String
    ): Flux<PermissionDto> {
        val pageRequest = Sort.by(Sort.Order(if (direction == "ASC") Sort.Direction.ASC else Sort.Direction.DESC, sort))
            .let { PageRequest.of(page, size, it) }
        return permissionsReactiveRepository.findAllBy(pageRequest)
            .map { it.toDto() }
    }

    @GetMapping("/permissions/{id}")
    fun getPermission(@PathVariable id: String): Mono<PermissionDto> {
        // note: ReactiveMongoRepository -> return permissionsReactiveRepository.findById(id)
        val permission = QPermission.permission
        val predicate = permission.id.eq(id)
        return permissionsReactiveRepository.findOne(predicate)
            .map { it.toDto() }
    }

    @DeleteMapping("/permissions/{id}")
    fun deletePermission(@PathVariable id: String): Mono<Void> = permissionsReactiveRepository.deleteById(id)

    @PostMapping("/permissions")
    fun createPermission(@RequestBody input: PermissionInput): Mono<PermissionDto> =
        permissionsReactiveRepository.save(input.toDocument()).map { it.toDto() }

    @PutMapping("/permissions/{id}")
    fun updatePermission(@PathVariable id: String, @RequestBody(required = true) input: PermissionInput): Mono<PermissionDto> =
        permissionsReactiveMongoTemplate.findAndModify(id, input)
            .switchIfEmpty(Mono.error(RuntimeException("Permission: $id Not Found")))
            .map { it.toDto() }
}

@Repository
interface PermissionsReactiveRepository : ReactiveMongoRepository<Permission, String>,
    ReactiveQuerydslPredicateExecutor<Permission> {
    fun findAllBy(pageable: Pageable): Flux<Permission>
    fun findByIdIn(ids: Set<String>): Flux<Permission>
}

@Component
class PermissionsReactiveMongoTemplate(val mongoTemplate: ReactiveMongoTemplate) {
    fun findAndModify(id: String, input: PermissionInput): Mono<Permission> {
        val query = Query(Criteria.where("id").`is`(id))
        val update = Update().set("name", input.name).set("description", input.description)
        // note: Update() has more `set` operations based on the document element type to be modified
        val options = FindAndModifyOptions.options().returnNew(true)
        // note: can be changed to `upsert`
        // ref: https://medium.com/geekculture/types-of-update-operations-in-mongodb-using-spring-boot-11d5d4ce88cf
        return mongoTemplate.findAndModify(query, update, options, Permission::class.java)
    }
}

@Repository
interface UserReactiveRepository : ReactiveMongoRepository<User, String>, ReactiveQuerydslPredicateExecutor<User> {
    fun findAllBy(pageable: Pageable): Flux<User>
}

@RestController
@RequestMapping("/reactive/")
@Validated
class ReactiveUserController(
    val userReactiveRepository: UserReactiveRepository,
    val permissionsReactiveRepository: PermissionsReactiveRepository,
    val applicationEventPublisher: ApplicationEventPublisher
) {
    @PostMapping("/users")
    fun createUser(@RequestBody input: UserInput): Mono<UserDto> {
        val permissions: Mono<List<Permission>> = Flux.fromIterable(input.permissionIds)
            .flatMap {
                    productId: String -> permissionsReactiveRepository.findById(productId)
                        .switchIfEmpty(Mono.error(RuntimeException("error: permission not found")))
            }
            .collectList()

        return Mono.just(input)
            .zipWith(permissions)
            .map { tuple ->
                User(id = null, name = tuple.t1.name, email = tuple.t1.email, permissions = tuple.t2
                    .map { permission -> permission.id!! }
                    .toSet()
                )
            }.flatMap { e -> userReactiveRepository.save(e) }
            .doOnSuccess { applicationEventPublisher.publishEvent(AppEvent("USER_CREATED_REACTIVE: ${it.id}")) }
            .zipWith(permissions)
            .map { tuple -> tuple.t1.toDto(tuple.t2) }
    }

    @PutMapping("/users/{id}")
    fun updateUser(@PathVariable id: String, @RequestBody input: UserInput): Mono<UserDto> {
        val permissions: Mono<List<Permission>> = Flux.fromIterable(input.permissionIds)
            .flatMap { productId: String -> permissionsReactiveRepository.findById(productId)
                .switchIfEmpty(Mono.error(RuntimeException("error: permission not found")))
            }
            .collectList()

        return userReactiveRepository.findById(id)
            .switchIfEmpty(Mono.error(RuntimeException("error: user not found")))
            .zipWith(permissions)
            .map {
                    tuple -> User(id = tuple.t1.id, name = input.name, email = input.email, permissions = tuple.t2.map { permission -> permission.id!! }.toSet())
            }.map { e -> userReactiveRepository.save(e) }
            .flatMap { e -> e }
            .zipWith(permissions)
            .map { tuple -> tuple.t1.toDto(tuple.t2) }
    }

    @DeleteMapping("/users/{id}")
    fun deleteUSer(@PathVariable id: String): Mono<Void> =
        userReactiveRepository.existsById(id)
            .filter { it == true }
            .switchIfEmpty(Mono.error(RuntimeException("error: user does not exists")))
            .flatMap { userReactiveRepository.deleteById(id) }

    @GetMapping("/users/{id}")
    fun getUserWithMappedPermissions(@PathVariable id: String): Mono<UserDto> {
        val user: Mono<User> = userReactiveRepository.findById(id)
        return user.map { e -> e.permissions }
            .map { e -> permissionsReactiveRepository.findByIdIn(e).collectList() }
            .map { e ->
                e.zipWith(user)
                    .map { tuple -> tuple.t2.toDto(tuple.t1) }
            }.flatMap { e -> e }
    }

    @GetMapping("/users")
    fun getAllUsersMapped(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String
    ): Flux<UserDto> {
        val pageRequest = Sort.by(Sort.Order(if (direction == "ASC") Sort.Direction.ASC else Sort.Direction.DESC, sort))
            .let { PageRequest.of(page, size, it) }
        return userReactiveRepository.findAllBy(pageRequest)
            .map { user ->
                user.toMono().zipWith(user.permissions.toMono())
                    .map { tuple ->
                        permissionsReactiveRepository.findByIdIn(tuple.t2).collectList()
                            .zipWith(tuple.t1.toMono())
                    }
            }.flatMap { e -> e }
            .flatMap { e -> e }
            .map { e -> e.t2.toDto(e.t1) }
    }

}