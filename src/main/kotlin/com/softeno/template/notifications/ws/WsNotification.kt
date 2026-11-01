package com.softeno.template.notifications.ws

import com.softeno.template.notifications.Message
import com.softeno.template.notifications.Notification
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * WebSocket adapter of the [Notification] port (ADR-0004) — delegates to the
 * session registry in [CoroutineMessageService]. Toggle: `com.softeno.ws.enabled`.
 */
@ConditionalOnProperty(name = ["com.softeno.ws.enabled"], havingValue = "true")
@Service
class WsNotification(
    private val messageService: CoroutineMessageService,
) : Notification {
    override fun broadcast(message: Message) {
        messageService.broadcast(message)
    }

    override fun revokeUser(userId: String): Int = messageService.revokeUser(userId)
}
