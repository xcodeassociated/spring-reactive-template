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
import reactor.core.publisher.Mono

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