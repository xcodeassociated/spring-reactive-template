package com.softeno.template.config.kafka

import org.springframework.boot.context.properties.ConfigurationProperties

/** Topic names wiring the users core to notifications (tx) and the Keycloak event stream. */
@ConfigurationProperties(prefix = "com.softeno.kafka")
data class KafkaApplicationProperties(val tx: String, val rx: String, val keycloak: String)
