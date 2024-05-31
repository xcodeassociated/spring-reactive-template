package com.softeno.template.app.kafka.config

import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.app.kafka.dto.KafkaMessage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter
import org.springframework.kafka.support.converter.JsonMessageConverter
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.sender.SenderOptions
import java.util.*


@ConfigurationProperties(prefix = "com.softeno.kafka")
data class KafkaApplicationProperties(val tx: String, val rx: String, val keycloak: String)

@Configuration
class JsonMessageConverterConfig {
    @Bean
    fun jsonMessageConverter(): JsonMessageConverter {
        return ByteArrayJsonMessageConverter()
    }
}

@Configuration
class ReactiveKafkaSampleConsumerConfig {
    @Bean(value = ["kafkaSampleOptions"])
    fun kafkaReceiverOptions(
        kafkaProperties: KafkaProperties,
        props: KafkaApplicationProperties
    ): ReceiverOptions<String, JsonNode> {
        val basicReceiverOptions: ReceiverOptions<String, JsonNode> =
            ReceiverOptions.create(kafkaProperties.buildConsumerProperties(null))
        return basicReceiverOptions.subscription(Collections.singletonList(props.rx))
    }

    @Bean(value = ["kafkaSampleConsumerTemplate"])
    fun reactiveKafkaConsumerTemplate(@Qualifier(value = "kafkaSampleOptions") kafkaReceiverOptions: ReceiverOptions<String, JsonNode>): ReactiveKafkaConsumerTemplate<String, JsonNode> {
        return ReactiveKafkaConsumerTemplate(kafkaReceiverOptions)
    }
}

@Configuration
class ReactiveKafkaSampleProducerConfig {
    @Bean(value = ["kafkaSampleProducerTemplate"])
    fun reactiveKafkaProducerTemplate(properties: KafkaProperties): ReactiveKafkaProducerTemplate<String, KafkaMessage> {
        val props = properties.buildProducerProperties(null)
        return ReactiveKafkaProducerTemplate<String, KafkaMessage>(SenderOptions.create(props))
    }
}

@Configuration
class ReactiveKafkaKeycloakConsumerConfig {
    @Bean(value = ["kafkaKeycloakOptions"])
    fun kafkaReceiverOptions(
        kafkaProperties: KafkaProperties,
        props: KafkaApplicationProperties
    ): ReceiverOptions<String, JsonNode> {
        val basicReceiverOptions: ReceiverOptions<String, JsonNode> =
            ReceiverOptions.create(kafkaProperties.buildConsumerProperties(null))
        return basicReceiverOptions.subscription(Collections.singletonList(props.keycloak))
    }

    @Bean(value = ["kafkaKeycloakConsumerTemplate"])
    fun reactiveKafkaConsumerTemplate(@Qualifier(value = "kafkaKeycloakOptions") kafkaReceiverOptions: ReceiverOptions<String, JsonNode>): ReactiveKafkaConsumerTemplate<String, JsonNode> {
        return ReactiveKafkaConsumerTemplate(kafkaReceiverOptions)
    }
}