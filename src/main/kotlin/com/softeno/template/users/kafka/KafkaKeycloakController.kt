package com.softeno.template.users.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.softeno.template.users.kafka.dto.KeycloakUserEvent
import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.CommandLineRunner
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux

@Controller
class ReactiveKafkaKeycloakController(
    @Qualifier(value = "kafkaKeycloakConsumerTemplate") private val reactiveKafkaConsumerTemplate: ReactiveKafkaConsumerTemplate<String, JsonNode>,
    private val objectMapper: ObjectMapper
): CommandLineRunner {
    private val log = LogFactory.getLog(javaClass)

    private fun consumeKafkaMessage(): Flux<JsonNode> {
        return reactiveKafkaConsumerTemplate
            .receiveAutoAck()
            .doOnNext { consumerRecord: ConsumerRecord<String, JsonNode> ->
                log.debug("[kafka] rx keycloak: ConsumerRecord: key=${consumerRecord.key()}, value=${consumerRecord.value()} from topic=${consumerRecord.topic()}, offset=${consumerRecord.offset()}")
            }
            .map { obj: ConsumerRecord<String, JsonNode> -> obj.value() }
            .doOnNext { message: JsonNode ->
                val dto: KeycloakUserEvent = objectMapper.readValue(message.toString(), KeycloakUserEvent::class.java)
                log.info("[kafka] rx keycloak: $dto")
            }
            .doOnError { throwable: Throwable ->
                log.error("[kafka] keycloak: ${throwable.message}")
            }
    }

    override fun run(vararg args: String) {
        log.info("[kafka]: keycloak consumer starts")
        consumeKafkaMessage().subscribe()
    }
}