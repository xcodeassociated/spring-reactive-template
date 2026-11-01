package com.softeno.template.notifications

/**
 * The notifications port (hexagon — see ADR-0004): ONE functionality,
 * implemented by one adapter per transport (`ws/WsNotification`,
 * `sse/SseNotification`). Producers (the Kafka listeners) depend on this
 * port only — adding a transport means adding an adapter, never touching
 * the event sources.
 */
interface Notification {
    /** Fan an event out to every live subscriber of this transport. */
    fun broadcast(message: Message)

    /**
     * Close everything live bound to [userId] (Keycloak `sub`) — logout/revocation.
     * Returns the number of sessions/streams closed; 0 when the transport has none.
     */
    fun revokeUser(userId: String): Int
}
