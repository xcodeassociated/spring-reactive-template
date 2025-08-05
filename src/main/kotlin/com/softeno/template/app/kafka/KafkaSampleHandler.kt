package com.softeno.template.app.kafka


import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.softeno.template.app.kafka.config.KafkaApplicationProperties
import com.softeno.template.app.user.notification.CoroutineUserUpdateEmitter
import com.softeno.template.app.user.notification.ReactiveUserUpdateEmitter
import com.softeno.template.sample.websocket.Message
import com.softeno.template.sample.websocket.WebSocketNotificationSender
import io.micrometer.tracing.Span
import io.micrometer.tracing.Tracer
import kotlinx.coroutines.DelicateCoroutinesApi
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
import reactor.kafka.sender.SenderResult

@JsonIgnoreProperties(ignoreUnknown = true)
data class KafkaMessage(val content: String, val traceId: String? = null, val spanId: String? = null)

fun KafkaMessage.toMessage() = Message(from = "SYSTEM", to = "ALL", content = this.content)

@Controller
class ReactiveKafkaSampleController(
    @param:Qualifier(value = "kafkaSampleConsumerTemplate") private val reactiveKafkaConsumerTemplate: ReactiveKafkaConsumerTemplate<String, JsonNode>,
    private val objectMapper: ObjectMapper,
    private val tracer: Tracer,
    private val ws: WebSocketNotificationSender,
    private val reactiveUserUpdateEmitter: ReactiveUserUpdateEmitter,
    private val userUpdateEmitter: CoroutineUserUpdateEmitter,
) : CommandLineRunner {
    private val log = LogFactory.getLog(javaClass)

    @OptIn(DelicateCoroutinesApi::class)
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
                    .traceId(kafkaMessage.traceId ?: RandomUtils.secure().toString())
                    .spanId(tracer.nextSpan().name("kafka-consumer").context().spanId())
                    .parentId(kafkaMessage.spanId ?: (tracer.currentSpan()?.context()?.spanId() ?: Span.NOOP.context().spanId()))
                    .sampled(true)
                    .build()

                tracer.currentTraceContext().newScope(contextWithCustomTraceId).use {
                    val span = tracer.nextSpan().name("kafka-consumer")
                    tracer.withSpan(span.start()).use {
                        log.info("[kafka] rx sample: $kafkaMessage")
                        ws.broadcast(kafkaMessage.toMessage())
                        reactiveUserUpdateEmitter.broadcast(kafkaMessage.toMessage())
                        userUpdateEmitter.broadcast(kafkaMessage.toMessage())
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
    @param:Qualifier(value = "kafkaSampleProducerTemplate") private val reactiveKafkaProducerTemplate: ReactiveKafkaProducerTemplate<String, KafkaMessage>,
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