package com.softeno.template.playground

import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext

class CoroutinePlayground {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @OptIn(DelicateCoroutinesApi::class)
    fun run() {
        logger.info(">> Coroutine playground started")

        // note: global `coroutineScope` exception handler
        val exceptionHandler = CoroutineExceptionHandler{ context: CoroutineContext, exception: Throwable ->
            logger.error(">> Global CoroutineExceptionHandler: $exception")
        }

        GlobalScope.launch(exceptionHandler) {
            val messages = listOf(helloCoroutine(), helloCoroutineWithContext())
            logger.info(">> coroutine message: $messages")

            val tasks = listOf(
                async { someComputation(logger) },
                async { someComputation(logger) }
            )
            tasks.awaitAll()

            val deferredWithTimeout = async { someComputation(logger) }
            runBlocking {
                try {
                    withTimeout(500) {
                        logger.info(">> start awaiting with 500 secs timeout")
                        deferredWithTimeout.join()
                    }
                } catch (exception: TimeoutCancellationException) {
                    logger.error(">> timeout")
                    deferredWithTimeout.cancel() // note: if the task will not be canceled it can still run
                }
            }

            val deferredWithError = supervisorScope {
                async { someError() }
            }
            // note: if the exception should not be handled by global scope handler `supervisorScope` has to be used
            try {
                deferredWithError.await()
            } catch (exception: Exception) {
                deferredWithError.cancel()
                logger.error(">> Handled local exception: $exception")
            }

            launch {
                logger.info(">> Coroutine playground ended")
            }.join()
            // note: launch - simple fire and forget scope - non-blocking unless `join`
        }

        // ...
    }

    suspend fun sampleFunction(input: String): String =
        withContext(Dispatchers.Default) {
            delay(2000)
            return@withContext input
        }
}

suspend fun helloCoroutine(): String {
    delay(500)
    return "hello"
}
suspend fun helloCoroutineWithContext(): String {
    return withContext(Dispatchers.IO) {
        delay(1000)
        val message = "coroutine!"
        return@withContext message
    }
}

suspend fun someComputation(logger: Logger) {
    withContext(Dispatchers.Default) {
        delay(2000)
        logger.info(">> Some Computation done!")
    }
}

suspend fun someError() {
    withContext(Dispatchers.Default) {
        delay(1000)
        throw RuntimeException("Some Error Occurred")
    }
}
