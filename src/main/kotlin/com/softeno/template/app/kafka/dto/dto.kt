package com.softeno.template.app.kafka.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class KafkaMessage(val content: String)

enum class KeycloakEventType {
    LOGIN,
    LOGOUT,
    REGISTER;

    companion object {
        @JsonCreator
        fun byString(input: String): KeycloakEventType? {
            return values().firstOrNull { it.name.equals(input, true) }
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class KeycloakUserEvent(
    val id: String,
    val time: Long,
    val type: KeycloakEventType,
    val realmId: String,
    val clientId: String?,
    val userId: String,
    val sessionId: String?,
    val ipAddress: String,
    val error: String?,
    val details: Map<String, String>
)
