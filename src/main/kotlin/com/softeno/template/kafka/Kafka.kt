package com.softeno.template.kafka

import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.sender.SenderOptions
import reactor.kafka.sender.SenderResult
import java.util.*
import java.util.function.Consumer


const val TOPIC_RX = "sample_topic_2"
const val TOPIC_TX = "sample_topic_2"

data class KafkaMessage(val content: String)

@Configuration
class ReactiveKafkaConsumerConfig {
    @Bean
    fun kafkaReceiverOptions(kafkaProperties: KafkaProperties): ReceiverOptions<String, KafkaMessage> {
        val basicReceiverOptions: ReceiverOptions<String, KafkaMessage> = ReceiverOptions.create(kafkaProperties.buildConsumerProperties())
        return basicReceiverOptions.subscription(Collections.singletonList(TOPIC_RX))
    }

    @Bean
    fun reactiveKafkaConsumerTemplate(kafkaReceiverOptions: ReceiverOptions<String, KafkaMessage>): ReactiveKafkaConsumerTemplate<String, KafkaMessage> {
        return ReactiveKafkaConsumerTemplate(kafkaReceiverOptions)
    }
}

@Configuration
class ReactiveKafkaProducerConfig {
    @Bean
    fun reactiveKafkaProducerTemplate(properties: KafkaProperties): ReactiveKafkaProducerTemplate<String, KafkaMessage> {
        val props = properties.buildProducerProperties()
        return ReactiveKafkaProducerTemplate<String, KafkaMessage>(SenderOptions.create(props))
    }
}

@Service
class ReactiveConsumerService(private val reactiveKafkaConsumerTemplate: ReactiveKafkaConsumerTemplate<String, KafkaMessage>): CommandLineRunner {
    private val log = LogFactory.getLog(javaClass)

    private fun consumeKafkaMessage(): Flux<KafkaMessage> {
        return reactiveKafkaConsumerTemplate
            .receiveAutoAck()
            .doOnNext { consumerRecord: ConsumerRecord<String, KafkaMessage> ->
                log.debug("[kafka] rx ConsumerRecord: key=${consumerRecord.key()}, value=${consumerRecord.value()} from topic=${consumerRecord.topic()}, offset=${consumerRecord.offset()}")
            }
            .map { obj: ConsumerRecord<String, KafkaMessage> -> obj.value() }
            .doOnNext { message: KafkaMessage ->
                log.info("[kafka] rx: $message")
            }
            .doOnError { throwable: Throwable ->
                log.error("[kafka]: ${throwable.message}")
            }
    }

    override fun run(vararg args: String) {
        log.info("[kafka]: consumer starts")
        consumeKafkaMessage().subscribe()
    }
}

@Service
class ReactiveKafkaProducerService(private val reactiveKafkaProducerTemplate: ReactiveKafkaProducerTemplate<String, KafkaMessage>) {
    private val log = LogFactory.getLog(javaClass)

    fun send(message: KafkaMessage) {
        log.info("[kafka] tx: topic: $TOPIC_TX, message: $message")
        reactiveKafkaProducerTemplate.send(TOPIC_TX, message)
            .doOnSuccess { senderResult: SenderResult<Void> ->
                log.info("[kafka] tx ok, offset: ${senderResult.recordMetadata().offset()}")
            }
            .subscribe()
    }
}
