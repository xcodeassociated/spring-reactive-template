package com.softeno.template.notifications.sse

import com.softeno.template.notifications.Message
import com.softeno.template.notifications.Notification
import org.springframework.stereotype.Service

/**
 * SSE adapter of the [Notification] port (ADR-0004) — fan-out through the
 * update emitter, revocation through the subscription registry.
 */
@Service
class SseNotification(
    private val emitter: CoroutineUserUpdateEmitter,
    private val registry: SseSubscriptionRegistry,
) : Notification {
    override fun broadcast(message: Message) {
        emitter.broadcast(message)
    }

    override fun revokeUser(userId: String): Int = registry.revokeUser(userId)
}
