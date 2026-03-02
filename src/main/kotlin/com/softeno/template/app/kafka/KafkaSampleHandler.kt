package com.softeno.template.app.kafka


import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.app.user.notification.CoroutineUserUpdateEmitter
import com.softeno.template.app.user.notification.ReactiveUserUpdateEmitter
import com.softeno.template.sample.websocket.Message
import com.softeno.template.sample.websocket.WebSocketNotificationSender
import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import tools.jackson.databind.ObjectMapper

@JsonIgnoreProperties(ignoreUnknown = true)
data class KafkaMessage(val content: String)

fun KafkaMessage.toMessage() = Message(from = "SYSTEM", to = "ALL", content = this.content)

@ConfigurationProperties(prefix = "com.softeno.kafka")
data class KafkaApplicationProperties(val tx: String, val rx: String, val keycloak: String)

@Controller
class KafkaSampleController(
    private val props: KafkaApplicationProperties,
    private val ws: WebSocketNotificationSender,
    private val reactiveUserUpdateEmitter: ReactiveUserUpdateEmitter,
    private val userUpdateEmitter: CoroutineUserUpdateEmitter,
    private val objectMapper: ObjectMapper
) {
    private val log = LogFactory.getLog(javaClass)

    @KafkaListener(id = "\${spring.kafka.consumer.group-id}", topics = ["\${com.softeno.kafka.rx}"])
    fun listen(record: ConsumerRecord<String, JsonNode>) {
        log.info("[kafka] rx (${props.rx}): ${record.key()}: ${record.value()}")

        val kafkaMessage: KafkaMessage = objectMapper.readValue(record.value().toString(), KafkaMessage::class.java)

        ws.broadcast(kafkaMessage.toMessage())
        reactiveUserUpdateEmitter.broadcast(kafkaMessage.toMessage())
        userUpdateEmitter.broadcast(kafkaMessage.toMessage())
    }
}

@Component
class KafkaSampleProducer(
    private val producer: KafkaTemplate<String, KafkaMessage>,
    private val props: KafkaApplicationProperties
) {
    private val log = LogFactory.getLog(javaClass)

    fun send(message: KafkaMessage) {
        log.info("[kafka] tx (${props.tx}): $message")
        producer.send(props.tx, message)
    }
}
