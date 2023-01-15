package com.softeno.template

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@SpringBootApplication
class SoftenoReactiveMongoApp

fun main(args: Array<String>) {
	runApplication<SoftenoReactiveMongoApp>(*args)
}

@RestController
class WebController(val permissionsReactiveRepository: PermissionsReactiveRepository) {
	@GetMapping("/")
	fun hello(): ResponseEntity<MessageDto> =
		ResponseEntity.ok(MessageDto(message = "hello world"));

	@GetMapping("/permissions")
	fun getAllPermissions(): Flux<Permission> = permissionsReactiveRepository.findAll()

	@GetMapping("/permissions/{id}")
	fun getPermission(@PathVariable id: String): Mono<Permission> = permissionsReactiveRepository.findById(id)

	@DeleteMapping("/permissions/{id}")
	fun deletePermission(@PathVariable id: String): Mono<Void> = permissionsReactiveRepository.deleteById(id)

	@PostMapping("/permissions")
	fun createPermission(@RequestBody input: PermissionInput): Mono<Permission> =
		permissionsReactiveRepository.save(Permission(id = null, name = input.name, description = input.description))
}

data class MessageDto(val message: String)
data class PermissionInput(val name: String, val description: String)
@Document
data class Permission(
	@Id
	val id: String?,
	val name: String,
	val description: String
)

@Repository
interface PermissionsReactiveRepository : ReactiveCrudRepository<Permission, String>
