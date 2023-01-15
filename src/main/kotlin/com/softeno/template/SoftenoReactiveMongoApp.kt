package com.softeno.template

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.annotation.Id
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.data.querydsl.ReactiveQuerydslPredicateExecutor
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.RequestPredicates.GET
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions.route
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.ok
import reactor.core.publisher.Flux
import reactor.core.publisher.Flux.fromIterable
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.core.publisher.toMono
import reactor.util.function.Tuple2


@SpringBootApplication
class SoftenoReactiveMongoApp

fun main(args: Array<String>) {
	runApplication<SoftenoReactiveMongoApp>(*args)
}

@Configuration
class RoutesConfig {
	@Bean
	fun routes(): RouterFunction<ServerResponse> {
		return route(GET("/")) { _: ServerRequest
			-> ok().body(BodyInserters.fromObject(MessageDto(message = "hello world")))
		}
		// .and(...) <- next router function
	}

	// note: there can be other bean router function added here
}

@RestController
@RequestMapping("/v1/")
@Validated
class WebControllerV1(
	val permissionsReactiveRepository: PermissionsReactiveRepository,
	val permissionsReactiveMongoTemplate: PermissionsReactiveMongoTemplate
) {
	@GetMapping("/permissions")
	fun getAllPermissions(
		@RequestParam(required = false, defaultValue = "0") page: Int,
		@RequestParam(required = false, defaultValue = "10") size: Int,
		@RequestParam(required = false, defaultValue = "id") sort: String,
		@RequestParam(required = false, defaultValue = "ASC") direction: String
	): Flux<Permission> {
		val sort = Sort.by(Sort.Order(if (direction == "ASC") Sort.Direction.ASC else Sort.Direction.DESC, sort))
		return permissionsReactiveRepository.findAllBy(PageRequest.of(page, size, sort))
	}

	@GetMapping("/permissions/{id}")
	fun getPermission(@PathVariable id: String): Mono<Permission> {
		// note: ReactiveMongoRepository -> return permissionsReactiveRepository.findById(id)
		val permission = QPermission.permission
		val predicate = permission.id.eq(id)
		return permissionsReactiveRepository.findOne(predicate)
	}

	@DeleteMapping("/permissions/{id}")
	fun deletePermission(@PathVariable id: String): Mono<Void> = permissionsReactiveRepository.deleteById(id)

	@PostMapping("/permissions")
	fun createPermission(@RequestBody input: PermissionInput): Mono<Permission> =
		permissionsReactiveRepository.save(Permission(id = null, name = input.name, description = input.description))

	@PatchMapping("/permissions/{id}")
	fun updatePermission(@PathVariable id: String, @RequestBody(required = true) input: PermissionInput): Mono<Permission> =
		permissionsReactiveMongoTemplate.findAndModify(id, input)
}

data class MessageDto(
	val message: String
)

data class PermissionInput(
	val name: String,
	val description: String
)

@Document
data class Permission(
	@Id
	val id: String?,

	@Indexed(unique = true)
	val name: String,

	val description: String
)

@Repository
interface PermissionsReactiveRepository : ReactiveMongoRepository<Permission, String>, ReactiveQuerydslPredicateExecutor<Permission> {
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

@Document
data class User(
	@Id
	val id: String?,
	val name: String,
	val permissions: Set<String>
)

data class UserInput(
	val name: String,
	val permissionIds: Set<String>
)

data class UserDto(
	val id: String,
	val name: String,
	val permissions: List<Permission>
)

@Repository
interface UserReactiveRepository : ReactiveMongoRepository<User, String>, ReactiveQuerydslPredicateExecutor<User> {
	fun findAllBy(pageable: Pageable): Flux<User>
}

@RestController
@RequestMapping("/v1/")
@Validated
class UserControllerV1(
	val userReactiveRepository: UserReactiveRepository,
	val permissionsReactiveRepository: PermissionsReactiveRepository
) {
	@GetMapping("/users")
	fun getAllUsers(
		@RequestParam(required = false, defaultValue = "0") page: Int,
		@RequestParam(required = false, defaultValue = "10") size: Int,
		@RequestParam(required = false, defaultValue = "id") sort: String,
		@RequestParam(required = false, defaultValue = "ASC") direction: String
	): Flux<User> {
		val sort = Sort.by(Sort.Order(if (direction == "ASC") Sort.Direction.ASC else Sort.Direction.DESC, sort))
		return userReactiveRepository.findAllBy(PageRequest.of(page, size, sort))
	}

	@PostMapping("/users")
	fun createUser(@RequestBody input: UserInput): Mono<User> {
		val permissions: Mono<List<Permission>> = fromIterable(input.permissionIds)
			.flatMap { productId: String -> permissionsReactiveRepository.findById(productId) }
			.collectList()

		return Mono.just(input.name)
			.zipWith(permissions)
			.map { tuple ->
				User(id = null, name = tuple.t1, permissions = tuple.t2
					.map { permission -> permission.id!! }
					.toSet()
				)
			}.flatMap { e -> userReactiveRepository.save(e) }
	}

	@GetMapping("/users/{id}/mapped")
	fun getUSerWithMappedPermissions(@PathVariable id: String): Mono<UserDto> {
		val user: Mono<User> = userReactiveRepository.findById(id)
		return user.map { e -> e.permissions }
			.map { e -> permissionsReactiveRepository.findByIdIn(e).collectList() }
			.map { e -> e.zipWith(user)
				.map { tuple -> UserDto(id = tuple.t2.id!!, name = tuple.t2.name, permissions = tuple.t1 ) }
			}.flatMap { e -> e }
	}

	@GetMapping("/users/mapped")
	fun getAllUsersMapped(
		@RequestParam(required = false, defaultValue = "0") page: Int,
		@RequestParam(required = false, defaultValue = "10") size: Int,
		@RequestParam(required = false, defaultValue = "id") sort: String,
		@RequestParam(required = false, defaultValue = "ASC") direction: String
	): Flux<UserDto> {
		val sort = Sort.by(Sort.Order(if (direction == "ASC") Sort.Direction.ASC else Sort.Direction.DESC, sort))
		return userReactiveRepository.findAllBy(PageRequest.of(page, size, sort))
			.map { user -> user.toMono().zipWith(user.permissions.toMono())
				.map { tuple -> permissionsReactiveRepository.findByIdIn(tuple.t2).collectList()
					.zipWith(tuple.t1.toMono())
				}
			}.flatMap { e -> e }
			.flatMap { e -> e }
			.map { e -> UserDto(id = e.t2.id!!, name = e.t2.name, permissions = e.t1) }

	}
	// (...)
}