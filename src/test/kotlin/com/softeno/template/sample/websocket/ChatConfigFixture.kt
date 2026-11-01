package com.softeno.template.sample.websocket

/**
 * [ChatConfigProperties] for tests — background sweeper disabled by default so
 * unit tests drive [CoroutineMessageService.sweep] explicitly.
 */
const val WS_ALLOWED_ORIGIN = "http://localhost:3000"

fun aChatConfig(
    staleCheck: Boolean = false,
    heartbeatIntervalSeconds: Int = 10,
    staleCheckThresholdSeconds: Int = 30,
    allowedOrigins: List<String> = listOf(WS_ALLOWED_ORIGIN),
): ChatConfigProperties = ChatConfigProperties(
    staleCheck = staleCheck,
    heartbeatIntervalSeconds = heartbeatIntervalSeconds,
    staleCheckThresholdSeconds = staleCheckThresholdSeconds,
    allowedOrigins = allowedOrigins,
)
