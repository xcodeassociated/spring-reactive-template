package com.softeno.template.notifications

import tools.jackson.databind.ObjectMapper

/**
 * Transport-neutral notification envelope — the wire format for both adapters
 * (JSON `{from, to, content}`; close codes / named SSE events carry control).
 */
data class Message(
    // `from` is stamped server-side from the session's bound identity on relay;
    // client-claimed identity is never trusted. Nullable with a default so minimal
    // control frames (e.g. pong: {"to":"SYSTEM","content":"pong"}) parse: Jackson
    // passes null (not the Kotlin default) for absent constructor properties.
    val from: String? = null,
    val to: String,
    val content: String,
) {
    init {
        require(to.isNotBlank()) { "Message 'to' cannot be blank" }
        require(content.isNotBlank()) { "Message 'content' cannot be blank" }
        require(from == null || from.isNotBlank()) { "Message 'from' cannot be blank" }
    }
}

fun Message.toJson(objectMapper: ObjectMapper): String = objectMapper.writeValueAsString(this)
