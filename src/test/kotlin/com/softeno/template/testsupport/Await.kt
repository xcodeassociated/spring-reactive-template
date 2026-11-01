package com.softeno.template.testsupport

/**
 * Polls [predicate] until it holds or the timeout elapses — for async effects
 * (flow emissions, sweeper passes, session unregistration) that have no directly
 * awaitable handle. Throws [AssertionError] with context on timeout.
 */
fun awaitUntil(
    timeoutMs: Long = 2_000,
    intervalMs: Long = 20,
    what: String = "condition",
    predicate: () -> Boolean
) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (predicate()) return
        Thread.sleep(intervalMs)
    }
    throw AssertionError("$what not met within ${timeoutMs}ms")
}
