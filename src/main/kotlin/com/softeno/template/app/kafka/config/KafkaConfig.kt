package com.softeno.template.app.kafka.config

import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.app.kafka.KafkaMessage
import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

@Configuration
class KafkaConsumerConfig {

    @Bean
    fun kafkaListenerContainerFactory(consumerFactory: ConsumerFactory<String, JsonNode>, registry: ObservationRegistry): ConcurrentKafkaListenerContainerFactory<String, JsonNode> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, JsonNode>()
        factory.setConsumerFactory(consumerFactory)
        factory.containerProperties.isObservationEnabled = true
        factory.containerProperties.observationRegistry = registry
        return factory
    }
}

@Configuration
class KafkaProducerConfig {

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, KafkaMessage>, registry: ObservationRegistry): KafkaTemplate<String, KafkaMessage> {
        return KafkaTemplate(producerFactory).apply {
            setObservationEnabled(true)
            setObservationRegistry(registry)
        }
    }
}