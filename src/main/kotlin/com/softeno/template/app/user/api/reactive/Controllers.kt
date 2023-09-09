package com.softeno.template.app.permission.api.reactive

import com.softeno.template.app.common.getPageRequest
import com.softeno.template.app.event.AppEvent
import com.softeno.template.app.permission.api.PermissionNotFoundException
import com.softeno.template.app.permission.db.PermissionDocument
import com.softeno.template.app.permission.db.PermissionsReactiveRepository
import com.softeno.template.app.permission.mapper.toDomain
import com.softeno.template.app.user.UserModifyCommand
import com.softeno.template.app.user.api.UserDto
import com.softeno.template.app.user.api.UserNotFoundException
import com.softeno.template.app.user.api.VersionMissingException
import com.softeno.template.app.user.db.UserDocument
import com.softeno.template.app.user.db.UserReactiveRepository
import com.softeno.template.app.user.mapper.toDomain
import com.softeno.template.app.user.mapper.toDto
import org.springframework.context.ApplicationEventPublisher
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono


@RestController
@RequestMapping("/reactive/")
@Validated
class ReactiveUserController(
    val userReactiveRepository: UserReactiveRepository,
    val permissionsReactiveRepository: PermissionsReactiveRepository,
    val applicationEventPublisher: ApplicationEventPublisher
) {
    @PostMapping("/users")
    fun createUser(@RequestBody input: UserModifyCommand): Mono<UserDto> {
        val permissions: Mono<List<PermissionDocument>> = Flux.fromIterable(input.permissionIds)
            .flatMap { productId: String ->
                permissionsReactiveRepository.findById(productId)
                    .switchIfEmpty(Mono.error(PermissionNotFoundException("error: permission not found")))
            }
            .collectList()

        return Mono.just(input)
            .zipWith(permissions)
            .map { tuple ->
                UserDocument(id = null,
                    name = tuple.t1.name,
                    email = tuple.t1.email,
                    permissions = tuple.t2
                        .map { permission -> permission.id!! }
                        .toSet(),
                    createdDate = null,
                    createdByUser = null,
                    lastModifiedDate = null,
                    modifiedByUser = null,
                    version = null
                )
            }.flatMap { e -> userReactiveRepository.save(e) }
            .doOnSuccess { applicationEventPublisher.publishEvent(AppEvent("USER_CREATED_REACTIVE: ${it.id}")) }
            .zipWith(permissions)
            .map { tuple -> tuple.t1.toDomain(tuple.t2.map { it.toDomain() }).toDto() }
    }

    @PutMapping("/users/{id}")
    fun updateUser(@PathVariable id: String, @RequestBody input: UserModifyCommand): Mono<UserDto> {
        val permissions: Mono<List<PermissionDocument>> = Flux.fromIterable(input.permissionIds)
            .flatMap { productId: String ->
                permissionsReactiveRepository.findById(productId)
                    .switchIfEmpty(Mono.error(PermissionNotFoundException("error: permission not found")))
            }
            .collectList()

        val version = input.version ?: throw VersionMissingException("error: version is null")

        return userReactiveRepository.findByIdAndVersion(id, version)
            .switchIfEmpty(Mono.error(UserNotFoundException("error: user not found")))
            .zipWith(permissions)
            .map { tuple ->
                UserDocument(
                    id = tuple.t1.id, name = input.name, email = input.email,
                    permissions = tuple.t2.map { permission -> permission.id!! }.toSet(),
                    createdDate = tuple.t1.createdDate, createdByUser = tuple.t1.createdByUser,
                    lastModifiedDate = tuple.t1.lastModifiedDate, modifiedByUser = tuple.t1.modifiedByUser,
                    version = tuple.t1.version
                )
            }.map { e -> userReactiveRepository.save(e) }
            .flatMap { e -> e }
            .zipWith(permissions)
            .map { tuple -> tuple.t1.toDomain(tuple.t2.map { it.toDomain() }).toDto() }
    }

    @DeleteMapping("/users/{id}")
    fun deleteUSer(@PathVariable id: String): Mono<Void> =
        userReactiveRepository.existsById(id)
            .filter { it == true }
            .switchIfEmpty(Mono.error(RuntimeException("error: user does not exists")))
            .flatMap { userReactiveRepository.deleteById(id) }

    @GetMapping("/users/{id}")
    fun getUserWithMappedPermissions(@PathVariable id: String): Mono<UserDto> {
        val userDocument: Mono<UserDocument> = userReactiveRepository.findById(id)
        return userDocument
            .switchIfEmpty(Mono.error(UserNotFoundException("error: user does not exists")))
            .map { e -> e.permissions }
            .map { e -> permissionsReactiveRepository.findByIdIn(e).collectList() }
            .map { e ->
                e.zipWith(userDocument)
                    .map { tuple -> tuple.t2.toDomain(tuple.t1.map { it.toDomain() }).toDto() }
            }.flatMap { e -> e }
    }

    @GetMapping("/users")
    fun getAllUsersMapped(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "10") size: Int,
        @RequestParam(required = false, defaultValue = "id") sort: String,
        @RequestParam(required = false, defaultValue = "ASC") direction: String
    ): Flux<UserDto> {
        return userReactiveRepository.findAllBy(getPageRequest(page, size, sort, direction))
            .map { user ->
                user.toMono().zipWith(user.permissions.toMono())
                    .map { tuple ->
                        permissionsReactiveRepository.findByIdIn(tuple.t2).collectList()
                            .zipWith(tuple.t1.toMono())
                    }
            }.flatMap { e -> e }
            .flatMap { e -> e }
            .map { e -> e.t2.toDomain(e.t1.map { it.toDomain() }).toDto() }
    }

    @GetMapping("/usersCount")
    fun getUsersSize(): Mono<Long> = userReactiveRepository.count()

}