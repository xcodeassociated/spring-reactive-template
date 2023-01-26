package com.softeno.template.kafka

import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
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


@ConfigurationProperties(prefix = "com.softeno.kafka")
@ConstructorBinding
data class KafkaApplicationProperties(val tx: String, val rx: String)

data class KafkaMessage(val content: String)

@Configuration
class ReactiveKafkaConsumerConfig {
    @Bean
    fun kafkaReceiverOptions(kafkaProperties: KafkaProperties, props: KafkaApplicationProperties): ReceiverOptions<String, KafkaMessage> {
        val basicReceiverOptions: ReceiverOptions<String, KafkaMessage> = ReceiverOptions.create(kafkaProperties.buildConsumerProperties())
        return basicReceiverOptions.subscription(Collections.singletonList(props.rx))
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
class ReactiveKafkaProducerService(private val reactiveKafkaProducerTemplate: ReactiveKafkaProducerTemplate<String, KafkaMessage>, private val props: KafkaApplicationProperties) {
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
