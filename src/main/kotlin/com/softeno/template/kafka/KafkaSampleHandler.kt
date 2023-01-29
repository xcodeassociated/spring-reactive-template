package com.softeno.template.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.CommandLineRunner
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.kafka.sender.SenderResult


@Controller
class ReactiveKafkaSampleController(
    @Qualifier(value = "kafkaSampleConsumerTemplate") private val reactiveKafkaConsumerTemplate: ReactiveKafkaConsumerTemplate<String, JsonNode>,
    private val objectMapper: ObjectMapper
): CommandLineRunner {
    private val log = LogFactory.getLog(javaClass)

    private fun consumeKafkaMessage(): Flux<JsonNode> {
        return reactiveKafkaConsumerTemplate
            .receiveAutoAck()
            .doOnNext { consumerRecord: ConsumerRecord<String, JsonNode> ->
                log.debug("[kafka] rx sample: ConsumerRecord: key=${consumerRecord.key()}, value=${consumerRecord.value()} from topic=${consumerRecord.topic()}, offset=${consumerRecord.offset()}")
            }
            .map { obj: ConsumerRecord<String, JsonNode> -> obj.value() }
            .doOnNext { message: JsonNode ->
                val dto: KafkaMessage = objectMapper.readValue(message.toString(), KafkaMessage::class.java)
                log.info("[kafka] rx sample: $dto")
            }
            .doOnError { throwable: Throwable ->
                log.error("[kafka] sample: ${throwable.message}")
            }
    }

    override fun run(vararg args: String) {
        log.info("[kafka] sample: consumer starts")
        consumeKafkaMessage().subscribe()
    }
}

@Service
class ReactiveKafkaSampleProducer(
    @Qualifier(value = "kafkaSampleProducerTemplate") private val reactiveKafkaProducerTemplate: ReactiveKafkaProducerTemplate<String, KafkaMessage>,
    private val props: KafkaApplicationProperties
) {
    private val log = LogFactory.getLog(javaClass)

    fun send(message: KafkaMessage) {
        log.info("[kafka] tx: topic: ${props.tx}, message: $message")
        reactiveKafkaProducerTemplate.send(props.tx, message)
            .doOnSuccess { senderResult: SenderResult<Void> ->
                log.info("[kafka] tx ok, offset: ${senderResult.recordMetadata().offset()}")
            }
            .subscribe()
    }
}