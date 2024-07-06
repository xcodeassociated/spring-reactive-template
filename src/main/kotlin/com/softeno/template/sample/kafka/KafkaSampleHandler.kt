package com.softeno.template.sample.kafka


import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.softeno.template.app.kafka.config.KafkaApplicationProperties
import com.softeno.template.app.kafka.dto.KafkaMessage
import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.Span
import io.micrometer.tracing.Tracer
import org.apache.commons.lang3.RandomUtils
import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.CommandLineRunner
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.sender.SenderResult


@Controller
class ReactiveKafkaSampleController(
    @Qualifier(value = "kafkaSampleConsumerTemplate") private val reactiveKafkaConsumerTemplate: ReactiveKafkaConsumerTemplate<String, JsonNode>,
    private val objectMapper: ObjectMapper,
    private val tracer: Tracer,
    private val observationRegistry: ObservationRegistry,
    @Qualifier(value = "kafkaSampleOptions") private val kafkaReceiverOptions: ReceiverOptions<String, JsonNode>
) : CommandLineRunner {
    private val log = LogFactory.getLog(javaClass)

    private fun consumeKafkaMessage(): Flux<JsonNode> {
        return reactiveKafkaConsumerTemplate
            .receiveAutoAck()
            .doOnNext { consumerRecord: ConsumerRecord<String, JsonNode> ->
                log.debug("[kafka] rx sample: ConsumerRecord: key=${consumerRecord.key()}, " +
                        "value=${consumerRecord.value()} from topic=${consumerRecord.topic()}, " +
                        "offset=${consumerRecord.offset()}, headers=${consumerRecord.headers()}")
            }
            .map { obj: ConsumerRecord<String, JsonNode> -> obj.value() }
            .doOnNext { message: JsonNode ->
                val kafkaMessage: KafkaMessage = objectMapper.readValue(message.toString(), KafkaMessage::class.java)

                // todo: make better observation handling by reactive kafka, currently the zipkin does not show the traces properly
                val contextWithCustomTraceId = tracer.traceContextBuilder()
                    .traceId(kafkaMessage.traceId ?: RandomUtils.nextLong().toString())
                    .spanId(tracer.nextSpan().name("kafka-consumer").context().spanId())
                    .parentId(kafkaMessage.spanId ?: (tracer.currentSpan()?.context()?.spanId() ?: Span.NOOP.context().spanId()))
                    .sampled(true)
                    .build()

                tracer.currentTraceContext().newScope(contextWithCustomTraceId).use {
                    val span = tracer.nextSpan().name("kafka-consumer")
                    tracer.withSpan(span.start()).use {
                        log.info("[kafka] rx sample: $kafkaMessage")
                        // additional processing ...
                    }
                    span.end()
                }
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